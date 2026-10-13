package com.ari.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.ari.assistant.agents.Agent
import com.ari.assistant.agents.AgentResult
import com.ari.assistant.agents.AgentTask
import com.ari.assistant.agents.PhoneCommands
import com.ari.assistant.agents.PhoneLauncher

/**
 * Agente de recados entre Ari: "dile a mi mamá que llego a las 8". Manda el SMS
 * directo (así lo pidió el dueño: estos recados no necesitan confirmación), solo a
 * contactos del teléfono y solo cuando habla el dueño (nunca un invitado).
 */
class AriMessageAgent(
    private val context: Context,
    private val phone: PhoneLauncher,
    private val ownerName: () -> String
) : Agent {

    /** true cuando faltan permisos (mandar SMS o contactos): la app los pide y lo vuelve a intentar. */
    @Volatile
    var needsPermission = false

    override val id = "mensajes"
    override val name = "Recados entre Ari"
    override val description = "Le manda un recado a otra persona por SMS; si tiene Ari, su Ari se lo dice."
    override val examples = listOf("dile a mi mamá que llego a las 8", "avísale a Laura que ya salí")

    override fun match(message: String, normalized: String): AgentTask? =
        AriMessages.request(message)?.let { AgentTask(message, ACTION, message) }

    override fun execute(task: AgentTask): AgentResult {
        val request = AriMessages.request(task.argument)
            ?: return AgentResult("No entendí el recado. Prueba: dile a mi mamá que llego a las 8.", success = false)
        if (!AriMessenger.canSend(context)) {
            needsPermission = true
            return AgentResult(
                "Para mandar recados necesito permiso de enviar SMS y de ver tus contactos. Si aceptas, lo mando enseguida.",
                success = false
            )
        }
        val contacts = phone.contacts() ?: run {
            needsPermission = true
            return AgentResult("Para saber quién es ${request.recipient} necesito ver tus contactos.", success = false)
        }
        val contact = when (val choice = PhoneCommands.pickContact(request.recipient, contacts)) {
            is PhoneCommands.ContactChoice.Found -> choice.contact
            is PhoneCommands.ContactChoice.Several -> return AgentResult(
                "Tienes varios contactos así: ${choice.contacts.joinToString(", ") { it.name }}. ¿A cuál? Dime el nombre completo.",
                success = false
            )
            PhoneCommands.ContactChoice.None ->
                return AgentResult("No encontré a \"${request.recipient}\" en tus contactos.", success = false)
        }
        val sms = AriMessages.encode(ownerName(), request.text)
        return if (AriMessenger.send(context, contact.number, sms)) {
            AgentResult("Listo, le mandé a ${contact.name}: \"${request.text}\". Si tiene Ari, ella se lo dice.")
        } else {
            AgentResult("No pude mandar el SMS. Revisa que tengas saldo o señal.", success = false)
        }
    }

    private companion object {
        const val ACTION = "recado"
    }
}

object AriMessenger {

    /** Quien muestra los recados que llegan mientras Ari está abierta (MainActivity). */
    interface Listener {
        /** true si el recado se vio en la pantalla de Ari (entonces no hace falta la notificación). */
        fun onAriMessage(text: String): Boolean
    }

    @Volatile
    var listener: Listener? = null

    private const val PREFS = "ari_recados"
    private const val KEY_INBOX = "pendientes"

    fun canSend(context: Context) = granted(context, Manifest.permission.SEND_SMS)

    fun canReceive(context: Context) = granted(context, Manifest.permission.RECEIVE_SMS)

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun send(context: Context, number: String, text: String): Boolean = try {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(text)
        if (parts.size == 1) manager.sendTextMessage(number, null, text, null, null)
        else manager.sendMultipartTextMessage(number, null, parts, null, null)
        true
    } catch (e: Exception) {
        false
    }

    /** El nombre de [number] en tus contactos, o null (si no está o no hay permiso). */
    fun contactName(context: Context, number: String): String? {
        if (!granted(context, Manifest.permission.READ_CONTACTS)) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }

    /** Guarda el recado para mostrarlo en el chat la próxima vez que abras Ari. */
    fun keep(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = prefs.getStringSet(KEY_INBOX, emptySet()).orEmpty().toMutableSet()
        list.add("${System.currentTimeMillis()}|$text")
        prefs.edit().putStringSet(KEY_INBOX, list.toSortedSet().toList().takeLast(20).toSet()).apply()
    }

    /** Los recados que llegaron con Ari cerrada, en orden; se borran al leerlos. */
    fun takeKept(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = prefs.getStringSet(KEY_INBOX, emptySet()).orEmpty()
        prefs.edit().remove(KEY_INBOX).apply()
        return list.sortedBy { it.substringBefore('|').toLongOrNull() ?: 0L }.map { it.substringAfter('|') }
    }

    internal fun deliver(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            val target = listener
            val seen = if (target != null) target.onAriMessage(text) else {
                keep(context, text)
                false
            }
            // Si no estás viendo a Ari, te llega en su chat de notificaciones.
            if (!seen) AriChat.ariSays(context, text)
        }
    }
}

/**
 * Recibe los SMS y solo hace caso a los que traen la marca de Ari ("[Ari de …]").
 * Los demás SMS ni se leen más allá de esa marca ni se guardan. Lo que dice un recado
 * es un mensaje para ti, nunca una orden para Ari.
 */
class AriSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        // Un SMS largo llega en partes: se juntan por número.
        messages.filterNotNull().groupBy { it.displayOriginatingAddress ?: "" }.forEach { (number, parts) ->
            val body = parts.joinToString("") { it.displayMessageBody ?: "" }
            val incoming = AriMessages.decode(body) ?: return@forEach
            val text = AriMessages.announce(AriMessenger.contactName(context, number), number, incoming)
            AriMessenger.deliver(context, text)
        }
    }
}
