package com.ari.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build

/**
 * El chat de Ari en las notificaciones: cuando termina algo y no estás en la app,
 * te escribe como en una app de mensajes, y le puedes contestar ahí mismo.
 *
 * No usa internet: todo pasa dentro del teléfono. Para contestar, Android pide
 * desbloquear el teléfono, así nadie le escribe a Ari en tu nombre.
 */
object AriChat {

    /** Quien contesta lo que escribes (MainActivity). null si Ari está cerrada. */
    interface Controller {
        fun onReply(text: String)
    }

    @Volatile
    var controller: Controller? = null

    private const val CHANNEL = "chat_ari"
    private const val OLD_CHANNEL = "avisos"
    private const val ID = 4_000_000
    private const val SHORTCUT = "chat_ari"
    internal const val KEY_REPLY = "respuesta"
    private const val MAX_LINES = 8
    private const val MAX_CHARS = 600

    private class Line(val fromAri: Boolean, val text: String, val time: Long)

    // Los últimos mensajes de la notificación (se borran al abrir Ari, donde está todo).
    private val lines = ArrayDeque<Line>()

    /** Ari te escribe. [alert] false: llega sin sonar ni asomarse (por ejemplo, en modo live). */
    fun ariSays(context: Context, text: String, alert: Boolean = true) {
        add(Line(true, clean(text), System.currentTimeMillis()))
        post(context, alert)
    }

    /** Lo que le contestaste aparece enseguida en el chat, mientras ella piensa. */
    fun youSaid(context: Context, text: String) {
        add(Line(false, clean(text), System.currentTimeMillis()))
        post(context, alert = false)
    }

    fun clear(context: Context) {
        synchronized(lines) { lines.clear() }
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    private fun clean(text: String): String {
        val flat = text.trim().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n")
        return if (flat.length > MAX_CHARS) flat.take(MAX_CHARS).trimEnd() + "…" else flat
    }

    private fun add(line: Line) = synchronized(lines) {
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    private fun post(context: Context, alert: Boolean) {
        if (!AlarmReminders.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(OLD_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Mensajes de Ari", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ari te escribe cuando termina algo, y le puedes contestar desde aquí."
            }
        )
        val snapshot = synchronized(lines) { lines.toList() }
        if (snapshot.isEmpty()) return
        val face = Icon.createWithResource(context, R.mipmap.ic_launcher)

        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Mutable solo para que Android le pase lo que escribiste; va directo al receptor de Ari.
        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val replyIntent = PendingIntent.getBroadcast(
            context, 2, Intent(context, AriChatReceiver::class.java), replyFlags
        )
        val reply = Notification.Action.Builder(face, "Responder", replyIntent)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Escríbele a Ari").build())
            .setAllowGeneratedReplies(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAuthenticationRequired(true)
            }
            .build()

        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Ari")
                    .setContentText("Ari te escribió")
                    .build()
            )
            .addAction(reply)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ari = Person.Builder().setName("Ari").setKey("ari").setIcon(face).setBot(true).build()
            val you = Person.Builder().setName("Tú").setKey("tu").build()
            val style = Notification.MessagingStyle(you).setConversationTitle("Ari")
            snapshot.forEach { style.addMessage(it.text, it.time, if (it.fromAri) ari else you) }
            builder.setStyle(style)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && publishShortcut(context, ari, face)) {
                // Así Android la muestra en "Conversaciones", como un chat.
                builder.setShortcutId(SHORTCUT)
            }
        } else {
            @Suppress("DEPRECATION")
            val style = Notification.MessagingStyle("Tú")
            @Suppress("DEPRECATION")
            snapshot.forEach { style.addMessage(it.text, it.time, if (it.fromAri) "Ari" else null) }
            builder.setStyle(style)
        }
        builder.setContentTitle("Ari").setContentText(snapshot.last().text)
        manager.notify(ID, builder.build())
    }

    private fun publishShortcut(context: Context, ari: Person, face: Icon): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val shortcut = ShortcutInfo.Builder(context, SHORTCUT)
                .setShortLabel("Ari")
                .setLongLived(true)
                .setPerson(ari)
                .setIcon(face)
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build()
            val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return false
            shortcuts.pushDynamicShortcut(shortcut)
            true
        } catch (e: RuntimeException) {
            false
        }
    }
}

/** Recibe lo que le contestas a Ari desde la notificación. Solo Android puede llamarlo. */
class AriChatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AriChat.KEY_REPLY)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        AriChat.youSaid(context, text)
        val ari = AriChat.controller
        if (ari == null) {
            AriChat.ariSays(context, "Ahora mismo estoy cerrada. Toca aquí para abrirme y seguimos.")
        } else {
            ari.onReply(text)
        }
    }
}
