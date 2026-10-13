package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechChunkerTest {

    @Test
    fun hablaFrasePorFraseMientrasLlega() {
        val buffer = StringBuilder()
        buffer.append("Claro que sí. Mañana va a ")
        assertEquals(listOf("Claro que sí."), SpeechChunker.take(buffer))
        assertEquals("Mañana va a ", buffer.toString())

        buffer.append("llover en la tarde. Lleva para")
        assertEquals(listOf("Mañana va a llover en la tarde."), SpeechChunker.take(buffer))

        buffer.append("guas")
        assertEquals(emptyList<String>(), SpeechChunker.take(buffer))
        assertEquals("Lleva paraguas", SpeechChunker.rest(buffer))
    }

    @Test
    fun frasesCortasSeJuntan() {
        val buffer = StringBuilder("Sí. Ya lo tengo listo para ti. ")
        assertEquals(listOf("Sí. Ya lo tengo listo para ti."), SpeechChunker.take(buffer))
    }

    @Test
    fun saltosDeLinea() {
        val buffer = StringBuilder("Primero compra pan\nDespués ve al banco\n")
        assertEquals(listOf("Primero compra pan", "Después ve al banco"), SpeechChunker.take(buffer))
    }

    @Test
    fun fraseLargaSeCortaEnLaComa() {
        val buffer = StringBuilder("Si sales temprano mañana por la mañana, llévate una chamarra porque va a hacer fr")
        assertEquals(listOf("Si sales temprano mañana por la mañana,"), SpeechChunker.take(buffer))
        assertEquals("llévate una chamarra porque va a hacer fr", buffer.toString())
    }

    @Test
    fun fraseCortaConComaEspera() {
        val buffer = StringBuilder("Mira, ")
        assertEquals(emptyList<String>(), SpeechChunker.take(buffer))
        assertEquals("Mira, ", buffer.toString())
    }

    @Test
    fun comaMuyAlPrincipioNoCorta() {
        val text = "Oye, déjame revisar con calma todo lo que tienes pendiente para esta semana"
        val buffer = StringBuilder(text)
        assertEquals(emptyList<String>(), SpeechChunker.take(buffer))
        assertEquals(text, buffer.toString())
    }
}
