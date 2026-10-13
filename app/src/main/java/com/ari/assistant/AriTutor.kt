package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Ari como tutora personal de cualquier tema: "quiero aprender inglés", "sé mi maestra
 * de guitarra". Mientras dura la clase, Gemma enseña paso a paso, pregunta, corrige y
 * al final de cada lección deja un resumen que Ari guarda para la próxima vez.
 * (Sin Android, para probarlo.)
 */
object AriTutor {

    private val START = listOf(
        Regex("^(?:quiero|quisiera|me gustaria)\\s+(?:aprender|estudiar|practicar)\\s+(?:sobre\\s+|a\\s+|de\\s+)?(.+)$"),
        Regex("^(?:ensename|ensenarme|explicame\\s+desde\\s+cero)\\s+(?!a\\s+usarte)(?!como\\s+(?:te|se)\\s+usa)(.+)$"),
        Regex("^(?:se|seras|quiero\\s+que\\s+seas)\\s+mi\\s+(?:tutora|maestra|profesora|profe|tutor|maestro|profesor)\\s+(?:de|en)\\s+(.+)$"),
        Regex("^(?:dame|quiero)\\s+(?:clases|una\\s+clase|lecciones)\\s+de\\s+(.+)$"),
        Regex("^ayudame\\s+a\\s+(?:aprender|estudiar|practicar)\\s+(.+)$"),
    )

    private val RESUME = Regex("^(?:sigamos|continuemos|seguimos|vamos)\\s+(?:con\\s+)?(?:la\\s+clase\\s+de\\s+|mi\\s+clase\\s+de\\s+|con\\s+)?(.+)$")

    private val STOP = setOf(
        "terminar clase", "termina la clase", "terminamos la clase", "fin de la clase", "salir de la clase",
        "ya no quiero clase", "ya acabamos la clase", "hasta aqui la clase", "terminemos la clase", "/fin"
    )

    /** "Quiero aprender inglés" → "inglés"; null si no es eso. */
    fun startTopic(text: String): String? {
        val clean = normalizeForAgents(text).replace(Regex("[¿?¡!.]"), "").trim()
        for (rule in START) {
            val topic = rule.find(clean)?.groupValues?.get(1)?.trim() ?: continue
            return tidy(original = text, normalizedTopic = topic)
        }
        return null
    }

    /** "Sigamos con inglés" → "inglés", solo si ya hay clases de ese tema. */
    fun resumeTopic(text: String, known: Collection<String>): String? {
        val clean = normalizeForAgents(text).replace(Regex("[¿?¡!.]"), "").trim()
        val topic = RESUME.find(clean)?.groupValues?.get(1)?.trim() ?: return null
        return known.firstOrNull { normalizeForAgents(it) == topic }
    }

    fun stops(text: String): Boolean = normalizeForAgents(text).replace(Regex("[¿?¡!.]"), "").trim() in STOP

    /** El tema con sus acentos, como lo escribiste, y sin cosas de más ("inglés por favor" → "inglés"). */
    private fun tidy(original: String, normalizedTopic: String): String? {
        val topic = normalizedTopic.removeSuffix(" por favor").removeSuffix(" porfa").trim()
        if (topic.isEmpty() || topic.length > 40 || topic.split(" ").size > 5) return null
        // Recupera las letras originales (acentos, ñ) buscando el mismo tramo en el texto escrito.
        val words = original.trim().trimEnd('.', '!', '?').split(Regex("\\s+"))
        val count = topic.split(" ").size
        for (start in 0..words.size - count) {
            val piece = words.subList(start, start + count).joinToString(" ")
            if (normalizeForAgents(piece) == topic) return piece
        }
        return topic
    }

    /** Lo que Gemma recibe como tutora (va en sus instrucciones de sistema). */
    fun instruction(topic: String, summary: String?, sessions: Int): String = buildString {
        append("AHORA ERES TUTORA PERSONAL DE \"").append(topic).append("\". ")
        if (sessions == 0 || summary.isNullOrBlank()) {
            append("Es la primera clase: saluda, pregunta en una frase qué nivel tiene y para qué lo quiere aprender, y empieza por ahí. ")
        } else {
            append("Ya llevan ").append(sessions).append(" clase(s). Resumen de lo último que aprendió: ")
            append(summary.trim().take(SUMMARY_MAX)).append(". Repasa eso en una pregunta corta y sigue con lo siguiente. ")
        }
        append(
            "Enseña en pasos cortos (una idea a la vez), con ejemplos de la vida diaria. Después de cada idea, " +
                "haz UNA pregunta o ejercicio y espera la respuesta. Corrige con cariño: di qué estuvo bien, qué no y por qué. " +
                "Adapta la dificultad a cómo responde. Si el tema es un idioma, escribe las frases en ese idioma " +
                "con su traducción al español y cómo se pronuncian. "
        )
        append(
            "Cada vez que termine un tema o la persona diga que ya es todo, escribe al final una sola línea que empiece con " +
                "\"$SUMMARY_TAG\" y diga en pocas palabras qué aprendió y qué sigue."
        )
    }

    const val SUMMARY_TAG = "RESUMEN DE LA CLASE:"
    private const val SUMMARY_MAX = 400

    /** El resumen que Gemma dejó al final de una respuesta, si lo hay. */
    fun summaryIn(response: String): String? =
        response.lines().lastOrNull { it.trim().uppercase().startsWith(SUMMARY_TAG.uppercase()) }
            ?.substringAfter(':')?.trim()?.trim('*', ' ')?.take(SUMMARY_MAX)?.ifEmpty { null }
}
