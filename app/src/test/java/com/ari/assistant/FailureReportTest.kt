package com.ari.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureReportTest {

    @Test
    fun informeConDatosDelTelefono() {
        val text = FailureReport.text(listOf("Teléfono" to "POCO X8", "Android" to "16"), "Ari se cerró", "java.lang.Error")
        assertTrue(text.contains("Teléfono: POCO X8"))
        assertTrue(text.contains("Qué pasó: Ari se cerró"))
        assertTrue(text.contains("java.lang.Error"))
    }

    @Test
    fun recortaLoLargo() {
        val text = FailureReport.text(emptyList(), "fallo", "x".repeat(20_000))
        assertTrue(text.length <= FailureReport.MAX_CHARS + 20)
        assertTrue(text.endsWith("[recortado]"))
    }

    @Test
    fun elMensajeDelErrorSeRecorta() {
        val secret = "a".repeat(1000)
        val trace = FailureReport.trace(IllegalStateException(secret, RuntimeException("causa")))
        assertFalse(trace.contains(secret))
        assertTrue(trace.contains("IllegalStateException"))
        assertTrue(trace.contains("Causado por: java.lang.RuntimeException: causa"))
    }

    @Test
    fun incluyeLaNotaDeQuienPrueba() {
        val text = FailureReport.text(emptyList(), "Reporte", "", note = "no me contesta")
        assertTrue(text.contains("no me contesta"))
    }
}
