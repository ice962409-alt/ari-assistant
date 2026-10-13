package com.ari.assistant.agents

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Responde la hora y la fecha del teléfono, que Gemma no conoce. */
class ClockAgent(private val now: () -> LocalDateTime = { LocalDateTime.now() }) : Agent {

    override val id = "reloj"
    override val name = "Agente de reloj"
    override val description = "Dice la hora y la fecha del teléfono."
    override val examples = listOf("qué hora es", "qué día es hoy")

    override fun match(message: String, normalized: String): AgentTask? = when {
        TIME_PATTERN.matches(normalized) -> AgentTask(message, ACTION_TIME)
        DATE_PATTERN.matches(normalized) -> AgentTask(message, ACTION_DATE)
        else -> null
    }

    override fun execute(task: AgentTask): AgentResult {
        val current = now()
        return when (task.action) {
            ACTION_TIME -> {
                val time = current.format(TIME_FORMAT)
                val verb = if (current.hour == 1) "Es la" else "Son las"
                AgentResult("$verb $time.")
            }

            ACTION_DATE -> AgentResult(
                "Hoy es ${current.format(DATE_FORMAT)}."
            )

            else -> AgentResult("Acción desconocida: ${task.action}", success = false)
        }
    }

    companion object {
        private const val ACTION_TIME = "hora"
        private const val ACTION_DATE = "fecha"

        private val SPANISH = Locale.forLanguageTag("es")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm", SPANISH)
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH)

        // Se compara con el texto normalizado (sin acentos ni signos).
        private val TIME_PATTERN = Regex(
            "^(?:que hora es|que horas son|dime la hora|me dices la hora)$"
        )
        private val DATE_PATTERN = Regex(
            "^(?:que (?:dia|fecha) es(?: hoy)?|cual es la fecha(?: de hoy)?|a que (?:dia )?estamos(?: hoy)?|que dia es hoy)$"
        )
    }
}
