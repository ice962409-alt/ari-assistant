package com.ari.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSendTest {

    @Test
    fun soloEnviaElRecadoExacto() {
        val recado = "[Ari de Javier] llego a las 8"
        assertTrue(AutoSend.matches("[Ari de Javier]  llego a las 8 ", recado))
        assertFalse(AutoSend.matches("[Ari de Javier] llego a las 9", recado))
        assertFalse(AutoSend.matches("hola", "hola"))
    }

    @Test
    fun unWhatsAppConfirmadoSoloSiEsElMismoTexto() {
        assertTrue(AutoSend.matches(" ya voy  para allá", "ya voy para allá", confirmed = true))
        assertFalse(AutoSend.matches("ya voy", "ya voy para allá", confirmed = true))
        assertFalse(AutoSend.matches("", "", confirmed = true))
        // Sin confirmar, solo los recados con la marca de Ari.
        assertFalse(AutoSend.matches("ya voy", "ya voy"))
    }
}
