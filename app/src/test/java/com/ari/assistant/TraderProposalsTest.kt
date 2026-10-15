package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraderProposalsTest {

    @Test
    fun understandsTheCommands() {
        assertEquals(
            TraderProposals.Request.Note("quiero alertas de precio"),
            TraderProposals.parse("anota una mejora: quiero alertas de precio")
        )
        assertEquals(TraderProposals.Request.Propose, TraderProposals.parse(norm("propón mejoras")))
        assertEquals(TraderProposals.Request.Clear, TraderProposals.parse("borra las mejoras"))
        assertNull(TraderProposals.parse("hola cómo estás"))
    }

    private fun norm(t: String) = com.ari.assistant.agents.normalizeForAgents(t)

    @Test
    fun collectsNotesFromGemma() {
        val response = "Te explico.\nMEJORA: poder leer varias gráficas a la vez\nFin."
        assertEquals(listOf("poder leer varias gráficas a la vez"), TraderProposals.notesIn(response))
    }

    @Test
    fun addsWithoutRepeating() {
        val one = TraderProposals.add(emptyList(), "alertas de precio")
        val two = TraderProposals.add(one, "Alertas de precio")
        assertEquals(1, two.size)
        assertEquals(2, TraderProposals.add(two, "diario por semana").size)
    }

    @Test
    fun buildsValidJsonMarkedAsProposal() {
        val body = TraderProposals.body(linkedMapOf("platform" to "android", "summary" to TraderProposals.MARK + "algo", "model" to ""))
        assertTrue(body.startsWith("{\"platform\":\"android\""))
        assertTrue(body.contains("Propuesta ARI Trader"))
        assertTrue(body.endsWith("}"))
    }

    @Test
    fun bodyFitsIn32Kb() {
        val body = TraderProposals.body(mapOf("platform" to "android", "details" to "x".repeat(40_000)))
        assertTrue(body.toByteArray().size <= 32 * 1024)
    }
}
