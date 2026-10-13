package com.ari.assistant

/**
 * Lo que Gemma recibe al empezar cada conversación: los últimos mensajes guardados,
 * recortados para que quepan en su memoria de corto plazo (sin Android, para probarlo).
 */
object GemmaContext {

    const val MAX_MESSAGES = 20
    const val MAX_TOTAL_CHARS = 4000
    const val MAX_MESSAGE_CHARS = 1200

    data class Turn(val fromUser: Boolean, val text: String)

    /**
     * Deja la plática lista para Gemma: empieza con un mensaje tuyo y termina con una
     * respuesta de Ari. Un mensaje tuyo sin respuesta al final se quita: es el que se
     * está enviando ahora, y si no, Gemma lo vería dos veces.
     */
    fun trimHistory(
        saved: List<Turn>,
        maxMessages: Int = MAX_MESSAGES,
        maxTotal: Int = MAX_TOTAL_CHARS,
        maxEach: Int = MAX_MESSAGE_CHARS
    ): List<Turn> {
        val recent = saved.filter { it.text.isNotBlank() }.takeLast(maxMessages).toMutableList()
        while (recent.isNotEmpty() && recent.last().fromUser) recent.removeAt(recent.size - 1)

        // De lo más nuevo a lo más viejo, hasta llenar el espacio.
        val kept = ArrayDeque<Turn>()
        var total = 0
        for (turn in recent.asReversed()) {
            val text = turn.text.trim().let { if (it.length > maxEach) it.take(maxEach) + "…" else it }
            if (total + text.length > maxTotal) break
            total += text.length
            kept.addFirst(Turn(turn.fromUser, text))
        }
        while (kept.isNotEmpty() && !kept.first().fromUser) kept.removeFirst()
        return kept.toList()
    }
}
