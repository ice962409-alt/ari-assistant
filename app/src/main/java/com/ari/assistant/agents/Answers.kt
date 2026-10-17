package com.ari.assistant.agents

/**
 * Las respuestas cortas a una pregunta de Ari ("¿Le mando…? Dime sí o no.", "¿Qué le escribo?"),
 * tal como llegan por voz: "sí, mándalo", "sale", "no, espera". Sin Android, para probarlo.
 */
object Answers {

    private val YES_START = setOf(
        "si", "sip", "dale", "va", "ok", "okay", "okey", "claro", "hazlo", "adelante", "confirmo", "sale",
        "simon", "andale", "correcto", "exacto", "perfecto", "listo", "aja", "mandalo", "mandaselo", "envialo",
        "enviaselo", "llama", "llamale", "marca", "marcale", "porfa"
    )
    private val NO_START = setOf(
        "no", "nop", "nel", "cancela", "cancelalo", "cancelar", "espera", "detente", "para", "olvidalo", "alto", "tampoco"
    )

    /** Un "sí" sin peros: "sí, mándalo", "dale", "sí por favor". "Sí, pero cambia…" no cuenta. */
    fun yes(text: String): Boolean {
        val words = words(text)
        if (words.isEmpty() || words.size > MAX_WORDS) return false
        if ("no" in words || "pero" in words) return false
        return words.first() in YES_START
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
