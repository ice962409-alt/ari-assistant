package com.ari.assistant

import com.ari.assistant.AriLessons.Lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriLessonsTest {

    @Test
    fun vanEnOrdenYSaltanLasHechas() {
        assertEquals(Lesson.CHAT, AriLessons.next(emptySet(), vision = true))
        assertEquals(Lesson.MEMORY, AriLessons.next(setOf("platicar", "recordatorio"), vision = true))
        val all = Lesson.values().map { it.id }.toSet()
        assertNull(AriLessons.next(all, vision = true))
    }

    @Test
    fun sinVistaNoHayLeccionDeFoto() {
        assertFalse(AriLessons.available(vision = false).contains(Lesson.PHOTO))
        val allButPhoto = Lesson.values().filter { it != Lesson.PHOTO }.map { it.id }.toSet()
        assertNull(AriLessons.next(allButPhoto, vision = false))
    }

    @Test
    fun cadaAgenteCumpleSuLeccion() {
        assertEquals(Lesson.REMINDER, AriLessons.completedBy("recordatorios"))
        assertEquals(Lesson.CHAT, AriLessons.completedBy("gemma"))
        assertNull(AriLessons.completedBy("reloj"))
    }

    @Test
    fun entiendeCuandoPidenAprender() {
        listOf("Enséñame", "¿Cómo te uso?", "tutorial", "Ari, enséñame a usarte").forEach { assertTrue(it, AriLessons.asks(it)) }
        assertFalse(AriLessons.asks("enséñame a cocinar arroz"))
    }

    @Test
    fun cadaLeccionTieneAlgoParaProbar() {
        Lesson.values().forEach {
            val text = AriLessons.prompt(it, 1, 8)
            assertTrue(text.contains(it.tryThis))
        }
    }
}
