package com.ari.assistant

import com.ari.assistant.AriUsage.AppTime
import com.ari.assistant.AriUsage.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriUsageTest {

    @Test
    fun understandsWhatYouAsk() {
        assertEquals(Request.SCREEN_TIME, AriUsage.parse("¿Cuánto tiempo usé el teléfono hoy?"))
        assertEquals(Request.SCREEN_TIME, AriUsage.parse("mi tiempo en pantalla"))
        assertEquals(Request.SCREEN_TIME, AriUsage.parse("¿En qué apps paso más tiempo?"))
        assertEquals(Request.STEPS, AriUsage.parse("¿Cuántos pasos llevo?"))
        assertEquals(Request.STEPS, AriUsage.parse("cuánto he caminado hoy"))
        assertNull(AriUsage.parse("¿qué tengo hoy?"))
    }

    @Test
    fun formatsTimes() {
        assertEquals("45 min", AriUsage.duration(45))
        assertEquals("2 h", AriUsage.duration(120))
        assertEquals("1 h 5 min", AriUsage.duration(65))
        val text = AriUsage.screenTimeMessage(listOf(AppTime("YouTube", 45), AppTime("WhatsApp", 60), AppTime("Reloj", 0)))
        assertEquals("Hoy usaste el teléfono 1 h 45 min. Lo que más: WhatsApp 1 h, YouTube 45 min.", text)
        assertTrue(AriUsage.screenTimeMessage(listOf(AppTime("TikTok", 400))).contains("descansar"))
    }

    @Test
    fun stepsStartFreshEachDayAndAfterRestart() {
        assertEquals(0L to 5000L, AriUsage.stepsToday(5000, "2026-10-13", 1000, "2026-10-14"))
        assertEquals(1500L to 5000L, AriUsage.stepsToday(6500, "2026-10-14", 5000, "2026-10-14"))
        // Se reinició el teléfono: el contador volvió a empezar.
        assertEquals(0L to 200L, AriUsage.stepsToday(200, "2026-10-14", 5000, "2026-10-14"))
    }
}
