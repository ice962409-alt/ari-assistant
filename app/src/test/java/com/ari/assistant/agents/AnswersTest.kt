package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswersTest {

    @Test
    fun siComoLoDices() {
        listOf("Sí", "¿Sí?", "sí, mándalo", "Dale", "sale", "sí por favor", "Ok", "mándaselo", "llámale").forEach {
            assertTrue(it, Answers.yes(it))
        }
        listOf("no", "sí, pero cambia el texto", "mejor no", "qué hora es", "").forEach {
            assertFalse(it, Answers.yes(it))
        }
    }

    @Test
    fun noComoLoDices() {
        listOf("No", "no, espera", "mejor no", "cancela", "olvídalo").forEach { assertTrue(it, Answers.no(it)) }
        listOf("sí", "noche de películas con mis amigos el sábado a las ocho de la noche").forEach { assertFalse(it, Answers.no(it)) }
    }

    @Test
    fun elMensajeSinElDileQue() {
        assertEquals("ya voy", Answers.messageBody("dile que ya voy"))
        assertEquals("hola", Answers.messageBody("Que diga: hola"))
        assertEquals("llego a las 8", Answers.messageBody("que le digas que llego a las 8"))
        assertEquals("Hola, ¿cómo estás?", Answers.messageBody("Hola, ¿cómo estás?"))
        assertEquals("¿qué onda?", Answers.messageBody("¿qué onda?"))
    }
}
