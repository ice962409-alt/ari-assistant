package com.ari.assistant

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Diario de la voz: los últimos pasos de escuchar y contestar ("escucha", "error 7",
 * "resultado", "en pausa"…), con la hora. Sirve para saber por qué Ari se quedó escuchando
 * sin hacer nada. No guarda audio, ni lo que dijiste, ni nombres. Vive solo en memoria y se
 * borra al cerrar Ari; se ve en Ajustes y va en el informe de fallo.
 * (Sin Android, para probarlo.)
 */
object VoiceTrace {

    const val MAX_ENTRIES = 60

    private val entries = ArrayDeque<String>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    @Synchronized
    fun add(event: String, now: LocalTime = LocalTime.now()) {
        entries.addLast("${now.format(clock)} ${event.take(60)}")
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    /** Los pasos, del más viejo al más nuevo; [limit] se queda con los últimos. */
    @Synchronized
    fun text(limit: Int = MAX_ENTRIES): String =
        if (entries.isEmpty()) "Sin datos todavía: usa la voz y vuelve a mirar."
        else entries.toList().takeLast(limit).joinToString("\n")

    @Synchronized
    fun clear() = entries.clear()
}
