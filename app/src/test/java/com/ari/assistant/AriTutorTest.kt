package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriTutorTest {

    @Test
    fun entiendeElTema() {
        assertEquals("inglés", AriTutor.startTopic("Quiero aprender inglés"))
        assertEquals("guitarra", AriTutor.startTopic("Ari, sé mi maestra de guitarra"))
        assertEquals("matemáticas", AriTutor.startTopic("Enséñame matemáticas"))
        assertEquals("cocina", AriTutor.startTopic("dame clases de cocina"))
        assertEquals("Excel", AriTutor.startTopic("ayúdame a aprender Excel por favor"))
    }

    @Test
    fun noConfundeConOtrasCosas() {
        assertNull(AriTutor.startTopic("Enséñame"))
        assertNull(AriTutor.startTopic("enséñame a usarte"))
        assertNull(AriTutor.startTopic("qué hora es"))
        assertNull(AriTutor.startTopic("quiero aprender a hacer una cosa muy larga que no es un tema corto"))
    }

    @Test
    fun seguirYTerminar() {
        assertEquals("inglés", AriTutor.resumeTopic("Sigamos con inglés", listOf("inglés", "guitarra")))
        assertNull(AriTutor.resumeTopic("sigamos con francés", listOf("inglés")))
        assertTrue(AriTutor.stops("Terminar clase"))
        assertFalse(AriTutor.stops("terminar la tarea"))
    }

    @Test
    fun guardaElResumen() {
        val response = "¡Muy bien!\n\nRESUMEN DE LA CLASE: los saludos en inglés; sigue: los números."
        assertEquals("los saludos en inglés; sigue: los números.", AriTutor.summaryIn(response))
        assertNull(AriTutor.summaryIn("Sin resumen"))
    }

    @Test
    fun laSegundaClaseRepasa() {
        val first = AriTutor.instruction("inglés", null, 0)
        assertTrue(first.contains("primera clase"))
        val later = AriTutor.instruction("inglés", "los saludos", 2)
        assertTrue(later.contains("los saludos"))
        assertTrue(later.contains(AriTutor.SUMMARY_TAG))
    }
}
