package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriMessagesTest {

    @Test
    fun entiendeElRecado() {
        assertEquals(AriMessages.Request("mamá", "llego a las 8"), AriMessages.request("Dile a mi mamá que llego a las 8."))
        assertEquals(AriMessages.Request("Laura", "ya salí"), AriMessages.request("Ari, avísale a Laura que ya salí"))
        assertEquals("Juan", AriMessages.request("mándale decir a Juan que lo quiero")?.recipient)
    }

    @Test
    fun noEsUnRecado() {
        assertNull(AriMessages.request("dime qué hora es"))
        assertNull(AriMessages.request("recuérdame llamar a mamá"))
        assertNull(AriMessages.request("manda un WhatsApp a Juan que diga hola"))
        // Con WhatsApp en la frase no es un recado entre Ari: va por WhatsApp.
        assertNull(AriMessages.request("dile a mi niña por WhatsApp que ya voy"))
        assertNull(AriMessages.request("dile a mi niña que ya voy por wasap"))
        // Mencionar WhatsApp sin usarlo de canal sigue siendo un recado.
        assertEquals(AriMessages.Request("Juan", "me pase su WhatsApp"), AriMessages.request("dile a Juan que me pase su WhatsApp"))
    }

    @Test
    fun ida_y_vuelta() {
        val sms = AriMessages.encode("Javier", "llego a las 8")
        assertEquals("[Ari de Javier] llego a las 8", sms)
        val incoming = AriMessages.decode(sms)!!
        assertEquals("Javier", incoming.claimedSender)
        assertEquals("llego a las 8", incoming.text)
    }

    @Test
    fun smsNormalNoSeToca() {
        assertNull(AriMessages.decode("Tu código es 1234"))
        assertNull(AriMessages.decode("[Ari de Javier]   "))
    }

    @Test
    fun elNombreEsElDeTusContactos() {
        val incoming = AriMessages.Incoming("Javier", "hola")
        assertEquals("Hijo te manda decir: hola", AriMessages.announce("Hijo", "5512345678", incoming))
        assertTrue(AriMessages.announce(null, "5512345678", incoming).contains("dice ser Javier"))
    }

    @Test
    fun recadoCompartidoDiceDeQuienDiceSer() {
        assertEquals("Recado de la Ari de Javier: hola", AriMessages.announceShared(AriMessages.Incoming("Javier", "hola")))
    }

    @Test
    fun recortaLoLargo() {
        assertTrue(AriMessages.encode("Ana", "x".repeat(1000)).length <= AriMessages.MAX_TEXT + 20)
    }
}
