package com.ari.assistant

/**
 * Corta la respuesta de Gemma en frases a medida que llega, para que Ari
 * empiece a hablar con la primera frase en vez de esperar la respuesta
 * completa. Así la conversación se siente como entre dos personas.
 */
object SpeechChunker {

    /** Una frase muy corta ("Sí.") se junta con la siguiente para que no suene entrecortado. */
    private const val MIN_LENGTH = 12

    /** Si una frase se alarga más que esto sin punto, Ari la corta en la última coma para no hacerte esperar. */
    private const val SOFT_LENGTH = 70

    /** Una pausa en coma solo si lo que va antes ya suena a frase. */
    private const val SOFT_MIN = 25

    // Fin de frase: . ! ? … : ; o salto de línea, seguido de espacio.
    private val END = Regex("(?<=[.!?…:;])\\s+|\\n+")

    /** Saca de [buffer] las frases completas y deja en él lo que todavía se está escribiendo. */
    fun take(buffer: StringBuilder): List<String> {
        val sentences = mutableListOf<String>()
        var start = 0
        var pending = StringBuilder()
        for (end in END.findAll(buffer)) {
            pending.append(buffer, start, end.range.first)
            start = end.range.last + 1
            if (pending.trim().length >= MIN_LENGTH) {
                sentences.add(pending.toString().trim())
                pending = StringBuilder()
            } else {
                pending.append(' ')
            }
        }
        var rest = pending.append(buffer, start, buffer.length).toString().trimStart()
        if (rest.length > SOFT_LENGTH) {
            val comma = rest.lastIndexOf(", ")
            if (comma >= SOFT_MIN) {
                sentences.add(rest.substring(0, comma + 1).trim())
                rest = rest.substring(comma + 2).trimStart()
            }
        }
        buffer.setLength(0)
        buffer.append(rest)
        return sentences
    }

    /** Lo que quedó al terminar la respuesta. */
    fun rest(buffer: StringBuilder): String {
        val text = buffer.toString().trim()
        buffer.setLength(0)
        return text
    }
}
