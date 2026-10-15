package com.ari.assistant

import com.ari.assistant.agents.ReminderParser
import com.ari.assistant.agents.normalizeForAgents

/**
 * Solo la ARI del dueño: él le dice a qué hora se reactivan los tokens de Claude y ARI le
 * avisa a esa hora que ya puede seguir trabajando con Claude (abrir la sesión y escribir "sigue").
 * Por dentro es un recordatorio normal, con un texto fijo. Sin Android, para probarlo.
 */
object TokenRelay {

    const val MESSAGE = "¡Ya puedes seguir con Claude! Abre la sesión de Claude Code y escribe \"sigue\"."

    /** Palabras que marcan la intención: tokens/reactivan + Claude, o "ya puedo/puedes seguir". */
    private val TRIGGER = Regex(
        "(?:los\\s+)?tokens?\\b.*\\b(?:se\\s+)?(?:reactivan|reactiven|reactivaran|recargan|renuevan|vuelven|regresan|activan)" +
            "|(?:se\\s+)?(?:reactivan|reactiven|recargan|renuevan)\\b.*\\btokens?" +
            "|(?:ya\\s+)?(?:puedo|puedes|podras|podre)\\s+(?:seguir|trabajar|continuar)\\b.*\\bclaude" +
            "|avisa(?:me)?\\b.*\\btokens?"
    )

    /** Los disparadores de hora que entiende el recordatorio. */
    private val TIME = Regex("\\b(a\\s+la\\s+una|a\\s+las?\\s+\\d|en\\s+\\d|hoy|manana|pasado\\s+manana|el\\s+(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo))")

    fun matches(text: String): Boolean = TRIGGER.containsMatchIn(normalizeForAgents(text))

    /**
     * Convierte la frase del usuario en un recordatorio "recuérdame <cuándo> <mensaje>".
     * Devuelve null si no se entiende la hora (para pedirla).
     */
    fun toReminder(text: String): String? {
        val plain = ReminderParser.plain(text)
        val at = TIME.find(plain)?.range?.first ?: return null
        // El trozo con la hora, tomado del texto original (misma longitud que `plain`).
        val timePart = text.substring(at).trim().trim('.', ',', '!', '?', '¡', '¿')
        return "recuérdame $timePart $MESSAGE"
    }
}
