package com.ari.assistant

import com.ari.assistant.GemmaContext.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaContextTest {

    @Test
    fun empiezaContigoYTerminaConAri() {
        val saved = listOf(
            Turn(false, "respuesta vieja"),
            Turn(true, "hola"),
            Turn(false, "¡hola!"),
            Turn(true, "lo que estoy enviando"),
        )
        val history = GemmaContext.trimHistory(saved)
        assertEquals(listOf(Turn(true, "hola"), Turn(false, "¡hola!")), history)
    }

    @Test
    fun respetaLosLimites() {
        val saved = (1..40).map { Turn(it % 2 == 1, "m$it " + "x".repeat(500)) }
        val history = GemmaContext.trimHistory(saved)
        assertTrue(history.size <= GemmaContext.MAX_MESSAGES)
        assertTrue(history.sumOf { it.text.length } <= GemmaContext.MAX_TOTAL_CHARS)
        assertTrue(history.first().fromUser)
        assertTrue(!history.last().fromUser)
        // Se quedan los más nuevos.
        assertTrue(history.last().text.startsWith("m40 "))
    }

    @Test
    fun recortaMensajesLargos() {
        val saved = listOf(Turn(true, "y".repeat(5000)), Turn(false, "ok"))
        val history = GemmaContext.trimHistory(saved)
        assertEquals(GemmaContext.MAX_MESSAGE_CHARS + 1, history.first().text.length)
    }

    @Test
    fun sinPlaticaNoHayHistorial() {
        assertTrue(GemmaContext.trimHistory(emptyList()).isEmpty())
        assertTrue(GemmaContext.trimHistory(listOf(Turn(true, "solo yo"))).isEmpty())
    }
}
