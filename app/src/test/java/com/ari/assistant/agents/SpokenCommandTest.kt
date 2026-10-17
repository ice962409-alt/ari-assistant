package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenCommandTest {

    private fun clean(text: String) = SpokenCommand.clean(text)

    @Test
    fun quitaElAriMalEscuchado() {
        // Captura del 17/10/2026: el reconocedor escribió "París" en vez de "Ari".
        assertEquals("Mándale un mensaje a mi niña por WhatsApp", clean("París Abre WhatsApp y Mándale un mensaje a mi niña"))
        assertEquals("pon una alarma a las 7", clean("Harry pon una alarma a las 7"))
        assertEquals("qué hora es?", clean("Ari, ¿qué hora es?"))
        assertEquals("abre WhatsApp", clean("oye Ari abre WhatsApp"))
    }

    @Test
    fun noTocaLoQueNoEsUnaOrden() {
        assertEquals("París es muy bonito", clean("París es muy bonito"))
        assertEquals("Harry Potter me gusta", clean("Harry Potter me gusta"))
        assertEquals("Ari", clean("Ari"))
        assertEquals("abre WhatsApp", clean("abre WhatsApp"))
        assertEquals("abre WhatsApp y Telegram", clean("abre WhatsApp y Telegram"))
    }

    @Test
    fun quitaElHacerDeLosPasos() {
        assertEquals(
            "mándale un mensaje a Fernanda que diga Hola, te saluda Ari, cómo estás ?",
            clean("Hacer : mándale un mensaje a Fernanda que diga Hola, te saluda Ari, cómo estás ?")
        )
    }

    @Test
    fun abreWhatsAppYMandaEsUnSoloWhatsApp() {
        assertEquals("mándale a Juan por WhatsApp que diga hola", clean("abre WhatsApp y mándale a Juan que diga hola"))
        assertEquals("dile a Ana por WhatsApp que ya voy", clean("Abre WhatsApp y dile a Ana que ya voy"))
        assertEquals("mándale un mensaje por WhatsApp a Ana", clean("abre WhatsApp y mándale un mensaje por WhatsApp a Ana"))
        // Y lo entiende el agente del teléfono.
        assertEquals(PhoneAction.WhatsAppContact("Juan", "hola"), PhoneCommands.parse(clean("abre WhatsApp y mándale a Juan que diga hola")))
        assertEquals(PhoneAction.WhatsAppAsk("mi niña"), PhoneCommands.parse(clean("París abre WhatsApp y mándale un mensaje a mi niña")))
    }
}
