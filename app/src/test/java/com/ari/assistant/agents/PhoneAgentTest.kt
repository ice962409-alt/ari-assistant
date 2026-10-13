package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAgentTest {

    private class FakePhone(private val works: Boolean = true) : PhoneLauncher {
        val launched = mutableListOf<Pair<PhoneAction, String?>>()
        override fun installedApps() = listOf(InstalledApp("WhatsApp", "com.whatsapp"), InstalledApp("Spotify", "com.spotify.music"))
        override fun launch(action: PhoneAction, app: InstalledApp?): Boolean {
            launched.add(action to app?.packageName)
            return works
        }
    }

    private fun PhoneAgent.say(text: String) = execute(match(text, normalizeForAgents(text))!!)

    @Test
    fun abreLaApp() {
        val phone = FakePhone()
        val result = PhoneAgent(phone).say("abre spotify")
        assertEquals("Abriendo Spotify.", result.reply)
        assertEquals("com.spotify.music", phone.launched.single().second)
    }

    @Test
    fun appQueNoExiste() {
        val phone = FakePhone()
        val result = PhoneAgent(phone).say("abre instagram")
        assertFalse(result.success)
        assertTrue(phone.launched.isEmpty())
    }

    @Test
    fun nuncaLlamaPorSuCuenta() {
        val result = PhoneAgent(FakePhone()).say("llama al 55 1234 5678")
        assertTrue(result.reply.contains("Toca llamar para confirmar"))
    }

    @Test
    fun avisaSiNoHayApp() {
        val result = PhoneAgent(FakePhone(works = false)).say("manda un whatsapp que diga hola")
        assertEquals("No encontré WhatsApp en tu teléfono.", result.reply)
    }

    @Test
    fun faltaElNumero() {
        val phone = FakePhone()
        val result = PhoneAgent(phone).say("llama a mamá")
        assertFalse(result.success)
        assertTrue(phone.launched.isEmpty())
    }
}
