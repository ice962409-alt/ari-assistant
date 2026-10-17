package com.ari.assistant.agents

/**
 * Las respuestas cortas a una pregunta de Ari ("¿Le mando…? Dime sí o no.", "¿Qué le escribo?"),
 * tal como llegan por voz: "sí, mándalo", "sale", "no, espera". Sin Android, para probarlo.
 */
object Answers {

    private val YES_START = setOf(
        "si", "sip", "dale", "va", "ok", "okay", "okey", "claro", "hazlo", "adelante", "confirmo", "sale",
        "simon", "andale", "correcto", "exacto", "perfecto", "listo", "aja", "mandalo", "mandaselo", "envialo",
        "enviaselo", "llamale", "marcale", "porfa"
    )
    private val NO_START = setOf(
        "no", "nop", "nel", "cancela", "cancelalo", "cancelar", "espera", "detente", "para", "olvidalo", "alto", "tampoco"
    )

    /**
     * Un "sí" sin peros: "sí, mándalo", "dale", "sí por favor". "Sí, pero cambia…" no cuenta, y
     * una orden nueva tampoco ("llama a Pedro", "ok, abre WhatsApp"): esa se atiende aparte.
     */
    fun yes(text: String): Boolean {
        val words = words(text)
        if (words.isEmpty() || words.size > MAX_WORDS) return false
        if ("no" in words || "pero" in words) return false
        if (words.first() !in YES_START) return false
        return !isCommand(text)
    }

    /** "no" o "cancela" a secas (una o dos palabras): "no llego hoy, perdón" es un mensaje, no un "no". */
    fun cancel(text: String): Boolean = words(text).size <= 2 && no(text)

    /** ¿Es una orden para el teléfono, tal cual o después de un "ok"/"sí" al principio? */
    private fun isCommand(text: String): Boolean {
        if (PhoneCommands.parse(text) != null) return true
        val rest = text.trim().trimStart('¿', '¡').replace(Regex("^\\S+[\\s,.:;!]+"), "")
        return rest.isNotBlank() && rest != text.trim() && PhoneCommands.parse(rest) != null
    }

    /** Un "no": "no", "mejor no", "cancela", "espera". */
    fun no(text: String): Boolean {
        val words = words(text)
        if (words.isEmpty() || words.size > MAX_WORDS) return false
        return words.first() in NO_START || (words.size >= 2 && words[0] == "mejor" && words[1] == "no")
    }

    /** El mensaje que dictaste a "¿Qué le escribo?", sin el "dile que" o "que diga" del principio. */
    fun messageBody(text: String): String {
        val trimmed = text.trim().trim('"', '“', '”', ' ')
        val plain = ReminderParser.plain(trimmed)
        val prefix = BODY_PREFIX.find(plain)?.range?.last?.plus(1) ?: 0
        return trimmed.substring(prefix).trim().trim('"', '“', '”', ':', ',', ' ')
    }

    private fun words(text: String): List<String> =
        normalizeForAgents(text).replace(Regex("[^a-z0-9 ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private const val MAX_WORDS = 6

    private val BODY_PREFIX = Regex(
        "^(?:(?:dile|ponle|escribele|mandale|que\\s+le\\s+digas|que\\s+le\\s+diga|que\\s+diga)\\b(?:\\s+que\\b)?[:,]?\\s*)"
    )
}
