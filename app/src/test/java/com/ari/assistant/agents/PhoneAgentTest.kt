package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAgentTest {

    private class FakePhone(
        private val works: Boolean = true,
        private val contacts: List<PhoneContact>? = null,
        /** Como la Ari CEO con Accesibilidad: envía y llama sola (después de tu "sí"). */
        private val direct: Boolean = false,
        private val hasWhatsApp: Boolean = true
    ) : PhoneLauncher {
        override fun contacts() = contacts
        val launched = mutableListOf<Pair<PhoneAction, String?>>()
        var armed: String? = null
        override fun installedApps() = listOf(InstalledApp("WhatsApp", "com.whatsapp"), InstalledApp("Spotify", "com.spotify.music"))
        override fun launch(action: PhoneAction, app: InstalledApp?): Boolean {
            if (action is PhoneAction.WhatsApp && !hasWhatsApp) return false
            launched.add(action to app?.packageName)
            return works
        }
        override val callsDirectly get() = direct
        override val sendsDirectly get() = direct
        override fun armSend(text: String, name: String) {
            armed = text
        }
        override fun disarmSend() {
            armed = null
        }
    }

    private fun PhoneAgent.answer(text: String) = execute(AgentTask(text, PhoneAgent.ANSWER, text))

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

    private val juan = listOf(PhoneContact("Juan Pérez", "5533333333"))

    @Test
    fun conAccesibilidadPreguntaAntesDeEnviar() {
        val phone = FakePhone(contacts = juan, direct = true)
        val agent = PhoneAgent(phone)
        val asked = agent.say("manda un whatsapp a juan que diga ya llegué")
        assertEquals("¿Le mando a Juan Pérez por WhatsApp: \"ya llegué\"? Dime sí o no.", asked.reply)
        assertTrue(phone.launched.isEmpty())
        assertTrue(agent.claims("Sí, mándalo"))
        val sent = agent.answer("Sí, mándalo")
        assertTrue(sent.success)
        assertEquals(PhoneAction.WhatsApp("5533333333", "ya llegué"), phone.launched.single().first)
        assertEquals("ya llegué", phone.armed)
        assertFalse(agent.waiting())
    }

    @Test
    fun conUnNoNoEnvia() {
        val phone = FakePhone(contacts = juan, direct = true)
        val agent = PhoneAgent(phone)
        agent.say("mándale un mensaje a juan que diga hola")
        assertTrue(agent.claims("no, espera"))
        assertEquals("Va, no lo mando.", agent.answer("no, espera").reply)
        assertTrue(phone.launched.isEmpty())
        assertEquals(null, phone.armed)
    }

    @Test
    fun otraCosaOlvidaLaPregunta() {
        val agent = PhoneAgent(FakePhone(contacts = juan, direct = true))
        agent.say("mándale un mensaje a juan que diga hola")
        assertFalse(agent.claims("qué hora es"))
        assertFalse(agent.waiting())
    }

    @Test
    fun laPreguntaCaduca() {
        var now = 0L
        val agent = PhoneAgent(FakePhone(contacts = juan, direct = true), clock = { now })
        agent.say("mándale un mensaje a juan que diga hola")
        now += PhoneAgent.WAIT_MS + 1
        assertFalse(agent.claims("sí"))
    }

    @Test
    fun preguntaQueLeEscribe() {
        val phone = FakePhone(contacts = juan)
        val agent = PhoneAgent(phone)
        assertEquals("¿Qué le escribo a Juan Pérez?", agent.say("mándale un mensaje a juan").reply)
        assertTrue(agent.claims("dile que ya voy"))
        val result = agent.answer("dile que ya voy")
        assertTrue(result.reply.contains("Revísalo y toca enviar"))
        assertEquals(PhoneAction.WhatsApp("5533333333", "ya voy"), phone.launched.single().first)
    }

    @Test
    fun loDictadoTambienSeConfirma() {
        val phone = FakePhone(contacts = juan, direct = true)
        val agent = PhoneAgent(phone)
        agent.say("manda un whatsapp a juan")
        assertTrue(agent.answer("llego en 10 minutos").reply.startsWith("¿Le mando a Juan Pérez por WhatsApp"))
        agent.answer("sí")
        assertEquals("llego en 10 minutos", phone.armed)
    }

    @Test
    fun yaConfirmadoNoVuelveAPreguntar() {
        val phone = FakePhone(contacts = juan, direct = true)
        val agent = PhoneAgent(phone)
        val text = "manda un whatsapp a juan que diga ya voy"
        val result = agent.execute(AgentTask(text, PhoneAgent.CONFIRMED, text))
        assertTrue(result.success)
        assertEquals("ya voy", phone.armed)
        assertFalse(agent.waiting())
    }

    @Test
    fun lasLlamadasTambienSeConfirman() {
        val phone = FakePhone(contacts = listOf(PhoneContact("Mamá", "5511111111")), direct = true)
        val agent = PhoneAgent(phone)
        assertEquals("¿Llamo a Mamá (5511111111)? Dime sí o no.", agent.say("llama a mamá").reply)
        assertTrue(phone.launched.isEmpty())
        assertEquals("Llamando a Mamá (5511111111)…", agent.answer("sí").reply)
        assertEquals(PhoneAction.Dial("5511111111"), phone.launched.single().first)
    }

    @Test
    fun sinWhatsAppDejaUnSms() {
        val phone = FakePhone(contacts = juan, hasWhatsApp = false)
        val result = PhoneAgent(phone).say("mándale un mensaje a juan que diga hola")
        assertTrue(result.reply.startsWith("No encontré WhatsApp: te dejé un SMS"))
        assertEquals(PhoneAction.Sms("5533333333", "hola"), phone.launched.single().first)
    }
}
