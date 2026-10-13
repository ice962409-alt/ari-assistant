package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAgentTest {

    private class FakePhone(
        private val works: Boolean = true,
        private val contacts: List<PhoneContact>? = null
    ) : PhoneLauncher {
        override fun contacts() = contacts
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
    fun sinPermisoDeContactosLoPide() {
        val phone = FakePhone()
        val agent = PhoneAgent(phone)
        val result = agent.say("llama a mamá")
        assertFalse(result.success)
        assertTrue(agent.needsContactsPermission)
        assertTrue(phone.launched.isEmpty())
    }

    @Test
    fun llamaAlContacto() {
        val phone = FakePhone(contacts = listOf(PhoneContact("Mamá", "5511111111")))
        val result = PhoneAgent(phone).say("llama a mamá")
        assertEquals("Te dejé marcado a Mamá (5511111111). Toca llamar para confirmar.", result.reply)
        assertEquals(PhoneAction.Dial("5511111111"), phone.launched.single().first)
    }

    @Test
    fun whatsappAlContacto() {
        val phone = FakePhone(contacts = listOf(PhoneContact("Juan Pérez", "5533333333")))
        val result = PhoneAgent(phone).say("manda un whatsapp a juan que diga ya llegué")
        assertTrue(result.success)
        assertEquals(PhoneAction.WhatsApp("5533333333", "ya llegué"), phone.launched.single().first)
    }

    @Test
    fun contactoQueNoExiste() {
        val phone = FakePhone(contacts = listOf(PhoneContact("Mamá", "5511111111")))
        val result = PhoneAgent(phone).say("llama a Pedro")
        assertFalse(result.success)
        assertTrue(phone.launched.isEmpty())
    }
}
