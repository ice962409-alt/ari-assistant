package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarAgentTest {

    private val day = LocalDate.of(2026, 10, 14)
    private var allowed = true
    private val items = listOf(
        CalendarItem("Dentista", LocalDateTime.of(2026, 10, 14, 16, 30), false),
        CalendarItem("Junta", LocalDateTime.of(2026, 10, 14, 9, 0), false),
        CalendarItem("Cumpleaños de Laura", LocalDateTime.of(2026, 10, 15, 0, 0), true),
    )
    private val agent = CalendarAgent(object : CalendarReader {
        override fun events(from: LocalDateTime, to: LocalDateTime) =
            if (!allowed) null else items.filter { !it.start.isBefore(from) && it.start.isBefore(to) }
    }) { day }

    private fun say(text: String): AgentResult? = agent.match(text, normalizeForAgents(text))?.let { agent.execute(it) }

    @Test
    fun hoyEnOrden() {
        val reply = say("¿Qué tengo hoy?")!!.reply
        assertTrue(reply.startsWith("Tienes 2 cosas hoy"))
        assertTrue(reply.indexOf("Junta") < reply.indexOf("Dentista"))
        assertTrue(reply.contains("a las 16:30"))
    }

    @Test
    fun mananaYTodoElDia() {
        val reply = say("qué tengo mañana")!!.reply
        assertTrue(reply.contains("todo el día: Cumpleaños de Laura"))
    }

    @Test
    fun diaLibre() {
        assertEquals("No tienes nada en tu calendario pasado mañana.", say("¿tengo algo pasado mañana?")!!.reply)
    }

    @Test
    fun sinPermisoLoPide() {
        allowed = false
        assertFalse(say("mi agenda de hoy")!!.success)
        assertTrue(agent.needsPermission)
    }

    @Test
    fun noConfundeConCrearCitas() {
        assertNull(agent.match("agenda una cita mañana a las 5", normalizeForAgents("agenda una cita mañana a las 5")))
        assertNull(agent.match("qué hora es", normalizeForAgents("qué hora es")))
    }
}
