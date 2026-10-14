package com.ari.assistant

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Ari lee sola los recados de otras Ari: mira las notificaciones de tu app de mensajes
 * (SMS) y solo hace caso a las que empiezan con "[Ari de …]". Las demás notificaciones
 * ni se leen más allá de eso ni se guardan. Necesita el permiso "Acceso a notificaciones"
 * que tú das en los ajustes del teléfono. Un recado es un mensaje para ti, nunca una orden.
 */
class AriRecadosListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName !in AriRecados.messagingApps(this)) return
        val extras = notification.notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.ifEmpty { null }
        AriRecados.texts(notification.notification).forEach { text ->
            val incoming = AriMessages.decode(text) ?: return@forEach
            if (!AriRecados.firstTime(text)) return@forEach
            val announcement = if (sender != null) "$sender te manda decir: ${incoming.text}"
            else AriMessages.announceShared(incoming)
            AriMessenger.deliver(this, announcement)
        }
    }
}

object AriRecados {

    /** Apps de mensajes conocidas, además de la que tengas como predeterminada para SMS. */
    private val KNOWN = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.android.mms", "com.android.messaging", "com.miui.mms"
    )

    fun messagingApps(context: Context): Set<String> =
        KNOWN + listOfNotNull(runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull())

    /** Los textos de una notificación (los mensajes de un chat, o su texto). */
    fun texts(notification: Notification): List<String> {
        val extras = notification.extras ?: return emptyList()
        val messages = runCatching {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.mapNotNull { (it as? android.os.Bundle)?.getCharSequence("text")?.toString() }
        }.getOrNull().orEmpty()
        if (messages.isNotEmpty()) return messages.takeLast(5)
        return listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
    }

    // La app de mensajes vuelve a publicar la misma notificación (al llegar otro SMS, al leerla):
    // cada recado se dice una sola vez.
    private val seen = LinkedHashSet<Int>()

    @Synchronized
    fun firstTime(text: String): Boolean {
        val key = text.trim().hashCode()
        if (key in seen) return false
        seen.add(key)
        while (seen.size > 50) seen.remove(seen.first())
        return true
    }

    fun component(context: Context) = ComponentName(context, AriRecadosListener::class.java)

    fun enabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(component(context)) == true
        } else {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(component(context).flattenToString()) == true
        }

    /** La pantalla de Android donde se da el "Acceso a notificaciones" a Ari. */
    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
}
