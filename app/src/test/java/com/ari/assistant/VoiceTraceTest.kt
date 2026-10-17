package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class VoiceTraceTest {

    @Test
    fun guardaLosUltimosPasosConLaHora() {
        VoiceTrace.clear()
        assertTrue(VoiceTrace.text().startsWith("Sin datos"))
        VoiceTrace.add("escucha", LocalTime.of(9, 5, 7))
        VoiceTrace.add("error 7", LocalTime.of(9, 5, 9))
        assertEquals("09:05:07 escucha\n09:05:09 error 7", VoiceTrace.text())
        assertEquals("09:05:09 error 7", VoiceTrace.text(limit = 1))
        repeat(VoiceTrace.MAX_ENTRIES + 5) { VoiceTrace.add("paso $it", LocalTime.NOON) }
        val lines = VoiceTrace.text().lines()
        assertEquals(VoiceTrace.MAX_ENTRIES, lines.size)
        assertEquals("12:00:00 paso ${VoiceTrace.MAX_ENTRIES + 4}", lines.last())
        VoiceTrace.clear()
    }
}
