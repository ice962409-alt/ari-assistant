package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriImprovementsTest {

    private fun parse(text: String) = AriImprovements.parse(normalizeForAgents(text))

    @Test
    fun findsTheNotesGemmaMarked() {
        val response = "No puedo leer tus correos todavía.\nMEJORA: leer los correos de Gmail sin internet\n**Mejora:** ok"
        assertEquals(listOf("leer los correos de Gmail sin internet"), AriImprovements.notesIn(response))
        assertEquals(emptyList<String>(), AriImprovements.notesIn("Todo bien, aquí está tu agenda."))
    }

    @Test
    fun understandsWhatYouAsk() {
        assertEquals(AriImprovements.Request.Note("que lea pdfs largos"), parse("Anota una mejora: que lea PDFs largos"))
        assertEquals(AriImprovements.Request.Propose, parse("Propón mejoras"))
        assertEquals(AriImprovements.Request.Propose, parse("¿Qué mejorarías?"))
        assertEquals(AriImprovements.Request.Propose, parse("tus mejoras"))
        assertEquals(AriImprovements.Request.Clear, parse("borra las mejoras"))
        assertNull(parse("mejora mi día"))
        assertNull(parse("¿qué tengo hoy?"))
    }

    @Test
    fun addsWithoutRepeatingAndWithALimit() {
        var notes = AriImprovements.add(emptyList(), "Leer PDFs largos")
        notes = AriImprovements.add(notes, "leer pdfs largos.")
        assertEquals(1, notes.size)
        repeat(40) { notes = AriImprovements.add(notes, "mejora $it") }
        assertEquals(AriImprovements.MAX_NOTES, notes.size)
        assertEquals("mejora 39", notes.last())
    }

    @Test
    fun theProposalHasEverythingClaudeNeeds() {
        val text = AriImprovements.proposal(listOf("leer pdfs largos", "poner alarmas repetidas"), "0.39.0", "POCO X8 Pro Max", "14/10/2026")
        assertTrue(text.contains("0.39.0"))
        assertTrue(text.contains("POCO X8 Pro Max"))
        assertTrue(text.contains("- leer pdfs largos"))
        assertTrue(text.contains("- poner alarmas repetidas"))
        assertTrue(text.contains("Pedido para Claude"))
    }
}
