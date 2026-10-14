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
}
