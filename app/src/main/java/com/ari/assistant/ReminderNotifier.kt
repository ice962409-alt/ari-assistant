package com.ari.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ari.assistant.agents.Reminder
import com.ari.assistant.agents.ReminderAlarms
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Programa los recordatorios con el AlarmManager de Android y los muestra
 * como notificación. Funciona sin internet y aunque Ari esté cerrada.
 */
class AlarmReminders(context: Context) : ReminderAlarms {

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(AlarmManager::class.java)

    override fun exact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    override fun schedule(reminder: Reminder) {
        val at = reminder.dueAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = pending(reminder.id)
        try {
            if (exact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                // Sin permiso de alarmas exactas, Android puede retrasarlo unos minutos.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override fun cancel(id: Long) {
        manager.cancel(pending(id))
    }

    private fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val EXTRA_ID = "recordatorio"
        private const val CHANNEL = "recordatorios"

        /**
         * Vuelve a programar todos los pendientes: al abrir Ari, al reiniciar el
         * teléfono o al actualizar la app (Android borra las alarmas en esos casos).
         * Los que se pasaron mientras el teléfono estaba apagado se avisan ya.
         */
        fun rescheduleAll(context: Context, database: AriDatabase) {
            val alarms = AlarmReminders(context)
            val now = LocalDateTime.now()
            database.pendingReminders().forEach { reminder ->
                if (reminder.dueAt.isAfter(now)) {
                    alarms.schedule(reminder)
                } else {
                    notify(context, reminder, late = true)
                    database.markReminderDone(reminder.id)
                }
            }
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun notify(context: Context, reminder: Reminder, late: Boolean = false) {
            if (!canNotify(context)) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Los recordatorios que le pides a Ari."
                }
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (late) "Ari: recordatorio atrasado" else "Ari te recuerda")
                .setContentText(reminder.text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(reminder.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .build()
            manager.notify(reminder.id.toInt(), notification)
        }
    }
}

/** Llega la hora de un recordatorio. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmReminders.EXTRA_ID, -1L)
        if (id < 0) return
        try {
            AriDatabase(context).use { database ->
                val reminder = database.pendingReminder(id) ?: return
                // Una alarma vieja con el mismo número (por ejemplo, tras recuperar un
                // respaldo) no debe avisar antes de tiempo: se vuelve a programar.
                if (reminder.dueAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
                    AlarmReminders(context).schedule(reminder)
                    return
                }
                AlarmReminders.notify(context, reminder)
                database.markReminderDone(id)
            }
        } catch (_: Throwable) {
            // Si la base de datos falla, no se cierra nada: el aviso se pierde, pero Ari sigue bien.
        }
    }
}

/** Al reiniciar el teléfono o actualizar Ari, Android borra las alarmas: se reprograman. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            AriDatabase(context).use { AlarmReminders.rescheduleAll(context, it) }
        } catch (_: Throwable) {
        }
    }
}

/**
 * "Ari terminó": si le dejas una tarea y sales de la app, te avisa cuando la
 * completa (una respuesta larga, un trabajo del equipo, Gemma lista).
 */
object TaskNotifier {
    private const val CHANNEL = "avisos"
    private const val ID = 4_000_000

    fun done(context: Context, title: String, text: String) {
        if (!AlarmReminders.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tareas terminadas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ari te avisa cuando termina algo que le dejaste haciendo."
            }
        )
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val preview = text.trim().replace(Regex("\\s+"), " ").take(300)
        manager.notify(
            ID,
            android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(preview)
                .setStyle(android.app.Notification.BigTextStyle().bigText(preview))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}
