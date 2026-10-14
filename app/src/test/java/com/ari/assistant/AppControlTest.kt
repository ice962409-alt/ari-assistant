package com.ari.assistant

import com.ari.assistant.AppControl.Command
import com.ari.assistant.AppControl.Direction
import com.ari.assistant.AppControl.GlobalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControlTest {

    @Test
    fun understandsTaps() {
        assertEquals(Command.Tap("siguiente"), AppControl.parse("Toca Siguiente"))
        assertEquals(Command.Tap("buscar"), AppControl.parse("Ari, dale a buscar"))
        assertEquals(Command.Tap("enviar"), AppControl.parse("toca el botón de enviar"))
        assertEquals(Command.Tap("aceptar"), AppControl.parse("presiona donde dice aceptar"))
    }

    @Test
    fun musicIsNotATap() {
        assertNull(AppControl.parse("toca música de Shakira"))
        assertNull(AppControl.parse("toca la canción despacito"))
    }

    @Test
    fun typesWhatYouSaidWithAccents() {
        assertEquals(Command.Type("Hola, ya voy en camino"), AppControl.parse("teclea Hola, ya voy en camino"))
        assertEquals(Command.Type("Mañana a las 8"), AppControl.parse("Escribe aquí: Mañana a las 8"))
        // "escribe un poema" sigue siendo para Gemma.
        assertNull(AppControl.parse("escribe un poema de amor"))
    }

    @Test
    fun scrollsAndGlobalActions() {
        assertEquals(Command.Scroll(Direction.DOWN), AppControl.parse("desliza hacia abajo"))
        assertEquals(Command.Scroll(Direction.UP), AppControl.parse("sube la pantalla"))
        assertEquals(Command.Scroll(Direction.DOWN), AppControl.parse("baja la pantalla"))
        assertEquals(Command.Scroll(Direction.LEFT), AppControl.parse("desliza a la izquierda"))
        assertEquals(Command.Global(GlobalAction.BACK), AppControl.parse("Atrás"))
        assertEquals(Command.Global(GlobalAction.HOME), AppControl.parse("ve al inicio"))
        assertEquals(Command.Global(GlobalAction.RECENTS), AppControl.parse("apps recientes"))
        assertEquals(Command.Global(GlobalAction.NOTIFICATIONS), AppControl.parse("abre las notificaciones"))
        assertEquals(Command.ReadScreen, AppControl.parse("¿Qué dice mi pantalla?"))
        assertNull(AppControl.parse("baja el volumen"))
        assertNull(AppControl.parse("¿qué tengo hoy?"))
    }

    @Test
    fun asksBeforeSensitiveTaps() {
        assertTrue(AppControl.isSensitive("enviar"))
        assertTrue(AppControl.isSensitive("Pagar ahora"))
        assertTrue(AppControl.isSensitive("eliminar chat"))
        assertFalse(AppControl.isSensitive("siguiente"))
        assertFalse(AppControl.isSensitive("buscar"))
        assertTrue(AppControl.isYes("Sí"))
        assertTrue(AppControl.isYes("dale"))
        assertTrue(AppControl.isNo("mejor no"))
        assertFalse(AppControl.isYes("no"))
    }

    @Test
    fun matchesButtonsOnScreen() {
        assertEquals(3, AppControl.matchScore("Siguiente", "siguiente"))
        assertEquals(2, AppControl.matchScore("Buscar en Google", "buscar"))
        assertEquals(1, AppControl.matchScore("Nueva búsqueda", "busqueda"))
        assertEquals(0, AppControl.matchScore("Ajustes", "buscar"))
    }

    @Test
    fun summarizesTheScreen() {
        val text = AppControl.summarize(listOf("Chats", "Mamá", "Mamá", "  Llego a las 8  ", "x"))
        assertEquals("Chats. Mamá. Llego a las 8", text)
    }
}
