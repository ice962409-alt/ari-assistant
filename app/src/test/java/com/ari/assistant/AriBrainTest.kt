package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriBrainTest {

    @Test
    fun encuentraLoAprendido() {
        val response = "¡Claro! Te dejo la receta.\n\n**APRENDÍ: prefiere recetas rápidas, de menos de 20 minutos.**"
        assertEquals(listOf("prefiere recetas rápidas, de menos de 20 minutos"), AriBrain.learnedIn(response))
        assertTrue(AriBrain.learnedIn("Sin nada nuevo.").isEmpty())
    }

    @Test
    fun noRepiteLoQueYaSabe() {
        val known = listOf("${AriBrain.PREFIX}Prefiere recetas rápidas, de menos de 20 minutos", "me gusta el café")
        assertTrue(AriBrain.alreadyKnown("prefiere recetas rápidas de menos de 20 minutos", known))
        assertFalse(AriBrain.alreadyKnown("trabaja de noche", known))
    }

    @Test
    fun enVozNoSeDiceLaNota() {
        val text = AriBrain.withoutNotes("Hola.\nAPRENDÍ: le gusta el fútbol")
        assertEquals("Hola.", text.trim())
    }
}
