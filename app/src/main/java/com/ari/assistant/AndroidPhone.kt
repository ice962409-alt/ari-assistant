package com.ari.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.app.SearchManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.ari.assistant.agents.InstalledApp
import com.ari.assistant.agents.PhoneAction
import com.ari.assistant.agents.PhoneContact
import com.ari.assistant.agents.PhoneLauncher
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Hace en Android lo que pide el agente del teléfono: abre apps y lanza
 * acciones con "intents" (los mensajes que Android usa para pedirle algo a
 * otra app). Ninguna acción se completa sola: el usuario confirma en la app.
 */
class AndroidPhone(context: Context) : PhoneLauncher {

    private val context = context.applicationContext
    private val mainThread = Handler(Looper.getMainLooper())

    override fun installedApps(): List<InstalledApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        return pm.queryIntentActivities(launcher, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
    }

    override fun contacts(): List<PhoneContact>? {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val result = mutableListOf<PhoneContact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null,
            // El número principal de cada contacto va primero.
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val number = cursor.getString(1)?.filter { it.isDigit() || it == '+' } ?: continue
                if (number.length >= 7) result.add(PhoneContact(name, number))
            }
        }
        return result
    }

    /** Solo la Ari CEO con su permiso de Accesibilidad toca "llamar" sola. */
    override val callsDirectly: Boolean
        get() = context.resources.getBoolean(com.ari.assistant.R.bool.ari_ceo) && AutoSend.enabled(context)

    override fun launch(action: PhoneAction, app: InstalledApp?): Boolean {
        val intents = intentsFor(action, app)
        if (intents.isEmpty()) return false
        if (action is PhoneAction.Dial && callsDirectly) AutoCall.arm(action.number)
        // Las pantallas se abren desde el hilo principal; el agente espera el resultado.
        var opened = false
        val done = CountDownLatch(1)
        mainThread.post {
            opened = intents.any { intent ->
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            done.countDown()
        }
        done.await(5, TimeUnit.SECONDS)
        return opened
    }

    /** Intents a probar, en orden: si el primero no tiene app, se intenta el siguiente. */
    private fun intentsFor(action: PhoneAction, app: InstalledApp?): List<Intent> = when (action) {
        is PhoneAction.OpenApp -> listOfNotNull(app?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) })
        is PhoneAction.Dial -> listOf(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(action.number))))
        is PhoneAction.WhatsApp -> whatsApp(action)
        is PhoneAction.Alarm -> listOf(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                .apply { if (action.label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, action.label) }
        )
        is PhoneAction.Timer -> listOf(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        )
        is PhoneAction.WebSearch -> listOf(
            Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", action.query),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query)))
        )
        is PhoneAction.Navigate -> listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(action.place))),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.place)))
        )
        PhoneAction.Camera -> listOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        is PhoneAction.Settings -> listOf(
            Intent(
                when (action.section) {
                    "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "sonido" -> Settings.ACTION_SOUND_SETTINGS
                    "pantalla" -> Settings.ACTION_DISPLAY_SETTINGS
                    "bateria" -> Intent.ACTION_POWER_USAGE_SUMMARY
                    "ubicacion" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "notificaciones" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
            ).apply {
                if (action.section == "notificaciones") putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
            Intent(Settings.ACTION_SETTINGS)
        )
        is PhoneAction.Sms -> listOf(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(action.number))).putExtra("sms_body", action.text)
        )
        is PhoneAction.Email -> listOf(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(action.to)))
                .apply { if (action.text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, action.text) }
        )
        is PhoneAction.PlayMusic -> {
            val search = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                .putExtra(SearchManager.QUERY, action.query)
            val pkg = MUSIC_PACKAGES[action.app]
            if (pkg != null) listOf(Intent(search).setPackage(pkg)) else listOf(search)
        }
        is PhoneAction.YouTube -> listOf(
            Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, action.query),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(action.query)))
        )
        is PhoneAction.CalendarEvent -> {
            val begin = action.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            listOf(
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, action.title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000L)
            )
        }
        // Las de contactos se resuelven en el agente antes de llegar aquí.
        is PhoneAction.Missing, is PhoneAction.DialContact, is PhoneAction.WhatsAppContact,
        is PhoneAction.SmsContact -> emptyList()
    }

    private fun whatsApp(action: PhoneAction.WhatsApp): List<Intent> {
        if (action.number.isEmpty()) {
            // Sin número, WhatsApp pregunta a quién mandarlo.
            return WHATSAPP_PACKAGES.map { pkg ->
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text).setPackage(pkg)
            }
        }
        val number = international(action.number)
        val uri = Uri.parse("https://wa.me/$number?text=" + Uri.encode(action.text))
        return WHATSAPP_PACKAGES.map { Intent(Intent.ACTION_VIEW, uri).setPackage(it) }
    }

    /** WhatsApp necesita el número con lada del país: 55 1234 5678 → 525512345678 en México. */
    private fun international(number: String): String {
        if (number.startsWith("+")) return number.drop(1)
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val country = listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }?.uppercase(Locale.ROOT)
        val e164 = country?.let { PhoneNumberUtils.formatNumberToE164(number, it) }
        return (e164 ?: number).removePrefix("+")
    }

    companion object {
        private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
        private val MUSIC_PACKAGES = mapOf(
            "spotify" to "com.spotify.music",
            "youtube music" to "com.google.android.apps.youtube.music",
            "deezer" to "deezer.android.app"
        )
    }
}
