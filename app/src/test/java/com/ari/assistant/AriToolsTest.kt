package com.ari.assistant

import com.ari.assistant.agents.PhoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriToolsTest {

    @Test
    fun readsTheStepsGemmaWrote() {
        val response = """
            Claro, le aviso a Rosa y te pongo la ruta.
            HACER: manda un WhatsApp a Rosa que diga llego tarde
            **HACER:** "cómo llego a casa".
            HACER: cómo llego a casa
            MEJORA: algo
        """.trimIndent()
        assertEquals(
            listOf("manda un WhatsApp a Rosa que diga llego tarde", "cómo llego a casa"),
            AriTools.stepsIn(response)
        )
    }

    @Test
    fun noStepsWhenJustTalking() {
        assertTrue(AriTools.stepsIn("Hola, ¿cómo estás? Hoy hace buen día.").isEmpty())
    }

    @Test
    fun atMostFourSteps() {
        val response = (1..6).joinToString("\n") { "HACER: abre app $it" }
        assertEquals(AriTools.MAX_STEPS, AriTools.stepsIn(response).size)
    }

    @Test
    fun safeStepsRunAloneAndMessagesAsk() {
        val plan = AriTools.plan(
            "HACER: cómo llego al aeropuerto\nHACER: pon Coldplay en Spotify\n" +
                "HACER: manda un WhatsApp a Rosa que diga ya voy\nHACER: compra boletos para el cine"
        )
        assertEquals(4, plan.size)
        val first = plan[0] as AriTools.Step.Run
        assertTrue(first.action is PhoneAction.Navigate)
        assertEquals(AriTools.Risk.SAFE, first.risk)
        assertEquals(AriTools.Risk.SAFE, (plan[1] as AriTools.Step.Run).risk)
        val message = plan[2] as AriTools.Step.Run
        assertTrue(message.action is PhoneAction.WhatsAppContact)
        assertEquals(AriTools.Risk.ASK, message.risk)
        assertTrue(plan[3] is AriTools.Step.Unknown)
    }

    @Test
    fun callsAndAgendaAsk() {
        assertEquals(AriTools.Risk.ASK, AriTools.risk(PhoneAction.DialContact("mamá")))
        assertEquals(AriTools.Risk.ASK, AriTools.risk(PhoneAction.Email("a@b.com", "hola")))
        assertEquals(AriTools.Risk.SAFE, AriTools.risk(PhoneAction.Ride("casa", "uber")))
        assertEquals(AriTools.Risk.SAFE, AriTools.risk(PhoneAction.Note("comprar leche")))
    }

    @Test
    fun describesWhatItWillAsk() {
        assertEquals("WhatsApp a Rosa: \"ya voy\"", AriTools.describe(PhoneAction.WhatsAppContact("Rosa", "ya voy")))
        assertEquals("Llamar a mamá", AriTools.describe(PhoneAction.DialContact("mamá")))
    }

    @Test
    fun instructionListsEveryFamily() {
        val text = AriTools.instruction()
        AriTools.FAMILIES.forEach { assertTrue(text.contains(it.name)) }
        assertTrue(text.contains(AriTools.TAG))
    }

    @Test
    fun avisaCuandoGemmaDiceQueYaLoMando() {
        // Captura del 17/10/2026: Gemma dijo que ya lo mandó y no había ningún paso.
        assertTrue(AriTools.claimsDone("Ya mandé el mensaje a Fernanda.\n\nAPRENDÍ: Javi prefiere mensajes directos."))
        assertTrue(AriTools.claimsDone("Listo, le he enviado tu mensaje."))
        assertTrue(AriTools.claimsDone("Mensaje enviado."))
        // Con el paso HACER sí lo hace ARI (después de tu sí): no es mentira.
        assertFalse(AriTools.claimsDone("Te lo preparo.\nHACER: manda un WhatsApp a Rosa que diga ya mandé el regalo"))
        assertFalse(AriTools.claimsDone("¿Quieres que te mande un mensaje?"))
        assertFalse(AriTools.claimsDone("Ya mero llegas, ánimo."))
    }
}
