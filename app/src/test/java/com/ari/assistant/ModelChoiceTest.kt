package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelChoiceTest {

    private val gib = 1024L * 1024 * 1024
    private val lotsOfSpace = 50_000_000_000L

    @Test
    fun telefonoPotenteUsaElCompleto() {
        // Un teléfono "de 12 GB" (como el POCO) le reporta a Android unos 11.
        val advice = ModelChoice.recommend(11 * gib, lotsOfSpace)
        assertEquals(ModelChoice.Model.E4B, advice.model)
        assertFalse(advice.basic)
        // Uno "de 8 GB" reporta unos 7.4.
        assertEquals(ModelChoice.Model.E4B, ModelChoice.recommend(7_900_000_000L, lotsOfSpace).model)
    }

    @Test
    fun telefonoMedianoUsaElLigeroYLaVersionBasica() {
        val advice = ModelChoice.recommend(5_600_000_000L, lotsOfSpace)
        assertEquals(ModelChoice.Model.E2B, advice.model)
        assertTrue(advice.basic)
        assertFalse(advice.tooWeak)
        assertTrue(ModelChoice.basic(5_600_000_000L))
        assertFalse(ModelChoice.basic(11 * gib))
    }

    @Test
    fun sinEspacioParaElCompletoRecomiendaElLigero() {
        val advice = ModelChoice.recommend(11 * gib, ModelChoice.Model.E2B.spaceNeeded + 1)
        assertEquals(ModelChoice.Model.E2B, advice.model)
        assertTrue(advice.reason.contains("no cabe"))
    }

    @Test
    fun pocaMemoriaAvisa() {
        val advice = ModelChoice.recommend(2 * gib, lotsOfSpace)
        assertTrue(advice.tooWeak)
        assertTrue(advice.reason.contains("lento"))
    }

    @Test
    fun elLigeroPesaMenos() {
        assertTrue(ModelChoice.Model.E2B.bytes < ModelChoice.Model.E4B.bytes)
        assertTrue(ModelChoice.Model.E2B.bytes > ModelDownloadText.MIN_VALID_BYTES)
    }
}
