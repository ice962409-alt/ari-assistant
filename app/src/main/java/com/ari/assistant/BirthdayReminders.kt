package com.ari.assistant

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ari.assistant.agents.PeopleParser
import com.ari.assistant.agents.Person
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Avisa un día antes de cada cumpleaños de la libreta de personas: "Mañana cumple
 * años Luis (5 de mayo)". Una revisión al día, a las 9 de la mañana, sin internet
 * y aunque Ari esté cerrada. No hace falta permiso de alarmas exactas.
 */
object BirthdayReminders {

    private const val PREFS = "ari_cumpleanos"
    private const val LAST_DAY = "ultimo_dia"
    private const val CHANNEL = "cumpleanos"
    private const val NOTIFICATION_ID = 7_700
    private val CHECK_AT: LocalTime = LocalTime.of(9, 0)

    /** Personas que cumplen años mañana. Un 29 de febrero se avisa el 28 en años no bisiestos. */
    fun tomorrow(people: List<Person>, today: LocalDate): List<Person> {
        val target = today.plusDays(1)
        return people.filter { person ->
            val (month, day) = person.birthday?.split("-")?.mapNotNull(String::toIntOrNull)
                ?.takeIf { it.size == 2 } ?: return@filter false
            if (month == 2 && day == 29 && !target.isLeapYear) target.monthValue == 2 && target.dayOfMonth == 28
            else target.monthValue == month && target.dayOfMonth == day
        }
    }

    /** El texto del aviso, o null si nadie cumple mañana. */
    fun message(people: List<Person>): String? {
        if (people.isEmpty()) return null
        val names = people.map { it.name.ifEmpty { it.relation } }
        val list = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " y " + names.last()
        val date = people.first().birthday?.let { PeopleParser.describeBirthday(it) }.orEmpty()
        val many = names.size > 1
        return "Mañana cumple${if (many) "n" else ""} años $list ($date). ¿${if (many) "Les" else "Le"} preparas algo?"
    }

    /** La próxima revisión: hoy a las 9 si todavía no pasa, o mañana. */
    fun nextCheck(now: LocalDateTime): LocalDateTime {
        val todayAt = now.toLocalDate().atTime(CHECK_AT)
        return if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
    }

    /** Programa la revisión de cada día. Se llama al abrir Ari y al reiniciar el teléfono. */
    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextCheck(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    /** Revisa la libreta y avisa (una sola vez por día). */
    fun check(context: Context, people: List<Person>, today: LocalDate = LocalDate.now()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_DAY, null) == today.toString()) return
        val text = message(tomorrow(people, today))
        prefs.edit().putString(LAST_DAY, today.toString()).apply()
        if (text == null || !AlarmReminders.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Cumpleaños", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Un día antes de cada cumpleaños de tu libreta."
            }
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Ari: cumpleaños mañana")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, BirthdayReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Llega la revisión diaria de cumpleaños: avisa si hace falta y programa la de mañana. */
class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val people = AriDatabase(context).use { it.people() }
            BirthdayReminders.check(context, people)
        } catch (_: Throwable) {
            // Si la base de datos falla, no se cierra nada: mañana se vuelve a intentar.
        }
        runCatching { BirthdayReminders.schedule(context) }
    }
}
