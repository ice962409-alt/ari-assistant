package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadTextTest {

    @Test
    fun progreso() {
        assertEquals(
            "Descargando a Gemma: 50% (1.8 de 3.7 GB)",
            ModelDownloadText.progress(ModelChoice.Model.E4B.bytes / 2, ModelChoice.Model.E4B.bytes)
        )
        assertTrue(ModelDownloadText.progress(0, 0).startsWith("Descargando a Gemma: 0%"))
    }

    @Test
    fun soloArchivoOficialPorHttps() {
        ModelChoice.Model.values().forEach {
            assertTrue(it.url.startsWith("https://huggingface.co/litert-community/"))
            assertTrue(it.url.endsWith(".litertlm"))
        }
    }

    @Test
    fun mensajesClaros() {
        assertTrue(ModelDownloadText.paused(3, 0, 1).contains("esperando Wi-Fi"))
        assertTrue(ModelDownloadText.failed(403).contains("Elegir un archivo"))
        assertTrue(ModelDownloadText.failed(1006).contains("espacio"))
        assertTrue(ModelDownloadText.noSpace(1_000_000_000L, 4_000_000_000L).contains("1.0 GB"))
    }
}
