package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriNewsTest {

    @Test
    fun soloLoNuevoDesdeLaUltimaVersion() {
        assertTrue(AriNews.since(40, 40).isEmpty())
        assertEquals(3, AriNews.since(39, 40).size)
        assertTrue(AriNews.since(35, 40).any { it.title.contains("tutora") })
    }

    @Test
    fun mensajeConFrasesParaProbar() {
        val text = AriNews.message(AriNews.since(39, 40), "0.27.0")
        assertTrue(text.contains("Novedades de Ari 0.27.0"))
        assertTrue(text.contains("¿qué tengo hoy?"))
    }

    @Test
    fun entiendePedirLosComandos() {
        assertTrue(AriNews.asksCommands("¿Qué te puedo decir?"))
        assertTrue(AriNews.asksCommands("comandos de voz"))
        assertFalse(AriNews.asksCommands("qué te puedo decir de mi día"))
        assertTrue(AriNews.commandsMessage().contains("Pon una alarma a las 7"))
    }
}
