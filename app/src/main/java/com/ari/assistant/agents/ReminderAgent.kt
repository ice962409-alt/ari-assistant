package com.ari.assistant.agents

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Reminder(val id: Long, val text: String, val dueAt: LocalDateTime)

/** Dónde se guardan los recordatorios (la base de datos en la app; una lista en las pruebas). */
interface ReminderStore {
    fun addReminder(text: String, dueAt: LocalDateTime): Long
    fun pendingReminders(): List<Reminder>
    fun cancelReminders(ids: List<Long>)
}

/** Quien programa el aviso en el teléfono (AlarmManager en la app). */
interface ReminderAlarms {
    fun schedule(reminder: Reminder)
    fun cancel(id: Long)
    /** false si Android solo deja avisos aproximados (pueden llegar unos minutos tarde). */
    fun exact(): Boolean
}

/**
 * Agente de recordatorios: "recuérdame mañana a las 9 llamar a mamá",
 * "mis recordatorios", "cancela el recordatorio de mamá". El aviso llega como
 * notificación aunque Ari esté cerrada.
 */
class ReminderAgent(
    private val store: ReminderStore,
    private val alarms: ReminderAlarms,
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) : Agent {

    override val id = "recordatorios"
    override val name = "Agente de recordatorios"
    override val description = "Te avisa con una notificación a la hora que le pidas."
    override val examples = listOf(
        "recuérdame mañana a las 9 llamar a mamá",
        "mis recordatorios",
        "cancela el recordatorio de mamá"
    )

    override fun match(message: String, normalized: String): AgentTask? = when {
        ReminderParser.isRequest(ReminderParser.plain(message.trim())) -> AgentTask(message, ACTION_ADD, message.trim())
        LIST_PATTERN.matches(normalized) -> AgentTask(message, ACTION_LIST)
        else -> CANCEL_PATTERN.matchEntire(normalized)?.let { found ->
            val all = found.groupValues[1].isNotEmpty()
            AgentTask(message, ACTION_CANCEL, if (all) ALL else found.groupValues[2].trim())
        }
    }

    override fun execute(task: AgentTask): AgentResult = when (task.action) {
        ACTION_ADD -> add(task.argument)
        ACTION_LIST -> list()
        ACTION_CANCEL -> cancel(task.argument)
        else -> AgentResult("Acción desconocida: ${task.action}", success = false)
    }

    private fun add(message: String): AgentResult =
        when (val parsed = ReminderParser.parse(message, now())) {
            is ReminderParser.Result.Problem -> AgentResult(parsed.message, success = false)
            is ReminderParser.Result.Ok -> {
                val id = store.addReminder(parsed.text, parsed.dueAt)
                alarms.schedule(Reminder(id, parsed.text, parsed.dueAt))
                val note = if (alarms.exact()) "" else
                    "\n\nOjo: tu teléfono solo permite avisos aproximados, así que puede llegar unos minutos tarde."
                AgentResult("Listo, te aviso ${describe(parsed.dueAt)}: ${parsed.text}.$note")
            }
        }

    private fun list(): AgentResult {
        val pending = store.pendingReminders()
        if (pending.isEmpty()) return AgentResult("No tienes recordatorios pendientes.")
        return AgentResult(
            "Tus recordatorios:" + pending.joinToString("") { "\n• ${describe(it.dueAt)}: ${it.text}" }
        )
    }

    private fun cancel(argument: String): AgentResult {
        if (argument.isEmpty()) {
            return AgentResult("¿Cuál cancelo? Dime, por ejemplo: cancela el recordatorio de mamá.", success = false)
        }
        // Sin acentos ni mayúsculas: "mama" encuentra "mamá".
        val needle = ReminderParser.plain(argument)
        val cancelled = store.pendingReminders().filter {
            argument == ALL || ReminderParser.plain(it.text).contains(needle)
        }
        store.cancelReminders(cancelled.map { it.id })
        cancelled.forEach { alarms.cancel(it.id) }
        return when (cancelled.size) {
            0 -> AgentResult("No encontré recordatorios con \"$argument\".", success = false)
            1 -> AgentResult("Cancelé el recordatorio: ${cancelled[0].text}.")
            else -> AgentResult("Cancelé ${cancelled.size} recordatorios.")
        }
    }

    /** "hoy a las 15:00", "mañana a las 9:00", "el viernes 16 de octubre a las 10:00". */
    fun describe(dueAt: LocalDateTime): String {
        val today = now().toLocalDate()
        val time = dueAt.format(TIME_FORMAT)
        return when (dueAt.toLocalDate()) {
            today -> "hoy a las $time"
            today.plusDays(1) -> "mañana a las $time"
            else -> "el ${dueAt.format(DATE_FORMAT)} a las $time"
        }
    }

    companion object {
        private const val ACTION_ADD = "agregar"
        private const val ACTION_LIST = "listar"
        private const val ACTION_CANCEL = "cancelar"

        private val SPANISH = Locale.forLanguageTag("es")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm", SPANISH)
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)

        // Se comparan con el texto normalizado (sin acentos ni signos).
        private val LIST_PATTERN = Regex(
            "^(?:mis recordatorios|que recordatorios tengo|(?:muestra|dime)(?:me)? (?:mis|los) recordatorios)$"
        )
        private val CANCEL_PATTERN = Regex(
            "^(?:cancela|borra|quita|elimina)r? (todos )?(?:el |los |mis )?recordatorios?(?: (?:del|de|para|que)\\b)?(.*)$"
        )
        private const val ALL = "*todos*"
    }
}
