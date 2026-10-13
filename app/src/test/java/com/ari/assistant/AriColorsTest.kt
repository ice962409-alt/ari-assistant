package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AriColorsTest {

    @Test
    fun entiendeElColorPedido() {
        assertEquals("rosa", AriColors.requested("Ponte rosa")?.id)
        assertEquals("lila", AriColors.requested("Ari, cambia tu color a lila")?.id)
        assertEquals("menta", AriColors.requested("píntate de verde")?.id)
        assertEquals("dorado", AriColors.requested("cambia el color a dorado")?.id)
    }

    @Test
    fun noConfundeOtrasFrases() {
        assertNull(AriColors.requested("pon música de Shakira"))
        assertNull(AriColors.requested("ponte las pilas"))
        assertNull(AriColors.requested("de qué color es el cielo"))
    }

    @Test
    fun colorDesconocidoVuelveAlDeSiempre() {
        assertEquals(AriColors.DEFAULT, AriColors.byId("negro"))
        assertEquals("rosa", AriColors.byId("rosa").id)
    }
}
