package com.ari.assistant.agents

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un evento de tu calendario. */
data class CalendarItem(val title: String, val start: LocalDateTime, val allDay: Boolean)

/** Lo que el agente necesita del teléfono. En la app lo hace Android; en las pruebas, una lista. */
interface CalendarReader {
    /** Los eventos entre [from] y [to]; null si aún no hay permiso para leer el calendario. */
    fun events(from: LocalDateTime, to: LocalDateTime): List<CalendarItem>?
}

/**
 * Agente de agenda: "¿qué tengo hoy?", "¿qué tengo mañana?", "mi agenda de la semana".
 * Solo lee tu calendario (para crear una cita está el agente del teléfono: "agenda…").
 */
class CalendarAgent(
    private val calendar: CalendarReader,
    private val today: () -> LocalDate = { LocalDate.now() }
) : Agent {

    /** true cuando falta el permiso de calendario: la app lo pide y lo vuelve a intentar. */
    @Volatile
    var needsPermission = false

    override val id = "agenda"
    override val name = "Agente de agenda"
    override val description = "Te dice lo que tienes en tu calendario hoy, mañana o esta semana."
    override val examples = listOf("qué tengo hoy", "qué tengo mañana", "mi agenda de la semana")

    override fun match(message: String, normalized: String): AgentTask? {
        val clean = normalized.replace(Regex("[¿?¡!.,]"), "").trim()
        val range = when {
            WEEK.containsMatchIn(clean) && ASK.containsMatchIn(clean) -> RANGE_WEEK
            AFTER_TOMORROW.containsMatchIn(clean) && ASK.containsMatchIn(clean) -> RANGE_AFTER_TOMORROW
            TOMORROW.containsMatchIn(clean) && ASK.containsMatchIn(clean) -> RANGE_TOMORROW
            TODAY_ONLY.matches(clean) || (TODAY.containsMatchIn(clean) && ASK.containsMatchIn(clean)) -> RANGE_TODAY
            else -> return null
        }
        return AgentTask(message, range)
    }

    override fun execute(task: AgentTask): AgentResult {
        val day = today()
        val (from, to, label) = when (task.action) {
            RANGE_TOMORROW -> Triple(day.plusDays(1), day.plusDays(2), "mañana")
            RANGE_AFTER_TOMORROW -> Triple(day.plusDays(2), day.plusDays(3), "pasado mañana")
            RANGE_WEEK -> Triple(day, day.plusDays(7), "en los próximos 7 días")
            else -> Triple(day, day.plusDays(1), "hoy")
        }
        val events = calendar.events(from.atStartOfDay(), to.atStartOfDay()) ?: run {
            needsPermission = true
            return AgentResult(
                "Para ver tu agenda necesito permiso para leer tu calendario. Si aceptas, te digo enseguida. " +
                    "Solo lo leo aquí en el teléfono.",
                success = false
            )
        }
        if (events.isEmpty()) return AgentResult("No tienes nada en tu calendario $label.")
        val sorted = events.sortedBy { it.start }
        val lines = sorted.take(MAX_ITEMS).joinToString("\n") { "• ${describe(it, task.action == RANGE_WEEK)}" }
        val more = if (sorted.size > MAX_ITEMS) "\n…y ${sorted.size - MAX_ITEMS} más." else ""
        val count = if (sorted.size == 1) "una cosa" else "${sorted.size} cosas"
        return AgentResult("Tienes $count $label:\n$lines$more")
    }

    private fun describe(item: CalendarItem, withDay: Boolean): String {
        val day = if (withDay) item.start.format(DAY_FORMAT) + ", " else ""
        val time = if (item.allDay) "todo el día" else "a las " + item.start.format(TIME_FORMAT)
        return "$day$time: ${item.title.ifBlank { "(sin título)" }}"
    }

    companion object {
        private const val RANGE_TODAY = "hoy"
        private const val RANGE_TOMORROW = "manana"
        private const val RANGE_AFTER_TOMORROW = "pasado"
        private const val RANGE_WEEK = "semana"
        private const val MAX_ITEMS = 8

        private val SPANISH = Locale.forLanguageTag("es")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm", SPANISH)
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d", SPANISH)

        // Preguntas por la agenda (no órdenes de crear una cita).
        private val ASK = Regex("\\b(?:que tengo|tengo algo|tengo pendiente|mi agenda|mis citas|mis eventos|mi calendario|que hay en mi (?:agenda|calendario)|que eventos tengo|que citas tengo)\\b")
        private val TODAY = Regex("\\bhoy\\b")
        private val TOMORROW = Regex("\\bmanana\\b")
        private val AFTER_TOMORROW = Regex("\\bpasado manana\\b")
        private val WEEK = Regex("\\b(?:semana|esta semana|proximos dias)\\b")
        private val TODAY_ONLY = Regex("^(?:que tengo|mi agenda|que hay en mi agenda|que hay en mi calendario|mis citas|que tengo pendiente)$")
    }
}
