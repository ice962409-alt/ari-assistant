package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriPremiumTest {

    @Test
    fun ownerHasEverythingAndOthersNeedToBuy() {
        assertTrue(AriPremium.has(ceo = true, purchased = false))
        assertFalse(AriPremium.has(ceo = false, purchased = false))
        assertTrue(AriPremium.has(ceo = false, purchased = true))
    }

    @Test
    fun theScreenListsFreeAndPremium() {
        val text = AriPremium.description()
        assertTrue(text.contains("Gratis, siempre"))
        assertTrue(text.contains("Contactos de recados sin límite"))
    }

    @Test
    fun ariNotesImprovementsForTheCommercialApp() {
        assertEquals(
            AriImprovements.Request.Note(AriImprovements.COMMERCIAL_TAG + "que tenga modo oscuro"),
            AriImprovements.parse(normalizeForAgents("Anota una mejora para Ari: que tenga modo oscuro"))
        )
    }
}
