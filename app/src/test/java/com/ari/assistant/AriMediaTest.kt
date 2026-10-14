package com.ari.assistant

import com.ari.assistant.AriMedia.Request
import com.ari.assistant.AriMedia.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AriMediaTest {

    @Test
    fun understandsPhotosAndFiles() {
        assertEquals(Request.Photos(When.YESTERDAY), AriMedia.parse("Muéstrame las fotos de ayer"))
        assertEquals(Request.Photos(When.TODAY), AriMedia.parse("fotos de hoy"))
        assertEquals(Request.Photos(When.WEEK), AriMedia.parse("enséñame mis fotos de esta semana"))
        assertEquals(Request.LastPhoto, AriMedia.parse("¿Cuál es mi última foto?"))
        assertEquals(Request.LastScreenshot, AriMedia.parse("muéstrame mi última captura de pantalla"))
        assertEquals(Request.Files("factura de luz"), AriMedia.parse("busca el archivo factura de luz"))
        assertEquals(Request.Files("contrato"), AriMedia.parse("¿Dónde está el documento contrato?"))
        assertNull(AriMedia.parse("toma una foto"))
        assertNull(AriMedia.parse("toma una captura"))
        assertNull(AriMedia.parse("¿qué tengo hoy?"))
    }

    @Test
    fun dayRanges() {
        val today = LocalDate.of(2026, 10, 14)
        val day = 24 * 60 * 60 * 1000L
        val start = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(start, AriMedia.millisRange(When.TODAY, today, ZoneOffset.UTC).first)
        assertEquals(start - day, AriMedia.millisRange(When.YESTERDAY, today, ZoneOffset.UTC).first)
        assertEquals(start - 1, AriMedia.millisRange(When.YESTERDAY, today, ZoneOffset.UTC).last)
        assertEquals(start - 6 * day, AriMedia.millisRange(When.WEEK, today, ZoneOffset.UTC).first)
    }
}
