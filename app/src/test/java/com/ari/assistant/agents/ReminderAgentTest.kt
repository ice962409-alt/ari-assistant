package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderAgentTest {

    private val now = LocalDateTime.of(2026, 10, 13, 10, 0)

    private class FakeStore : ReminderStore {
        val items = mutableListOf<Reminder>()
        override fun addReminder(text: String, dueAt: LocalDateTime): Long {
            val id = items.size + 1L
            items.add(Reminder(id, text, dueAt))
            return id
        }
        override fun pendingReminders() = items.toList()
        override fun cancelReminders(ids: List<Long>) {
            items.removeAll { it.id in ids }
        }
    }

    private class FakeAlarms(private val exact: Boolean = true) : ReminderAlarms {
        val scheduled = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
        override fun schedule(reminder: Reminder) { scheduled.add(reminder.id) }
        override fun cancel(id: Long) { cancelled.add(id) }
        override fun exact() = exact
    }

    private fun AgentTask?.run(agent: ReminderAgent) = agent.execute(this!!)

    private fun ReminderAgent.say(message: String) = match(message, normalizeForAgents(message))

    @Test
    fun programaYConfirma() {
        val store = FakeStore()
        val alarms = FakeAlarms()
        val agent = ReminderAgent(store, alarms) { now }
        val result = agent.say("Recuérdame mañana a las 9 llamar a mamá").run(agent)
        assertTrue(result.success)
        assertEquals("Listo, te aviso mañana a las 9:00: llamar a mamá.", result.reply)
        assertEquals(listOf(1L), alarms.scheduled)
    }

    @Test
    fun avisaSiNoEsExacto() {
        val agent = ReminderAgent(FakeStore(), FakeAlarms(exact = false)) { now }
        assertTrue(agent.say("recuérdame en 5 minutos el té").run(agent).reply.contains("unos minutos tarde"))
    }

    @Test
    fun sinHoraNoProgramaNada() {
        val alarms = FakeAlarms()
        val agent = ReminderAgent(FakeStore(), alarms) { now }
        val result = agent.say("recuérdame llamar a mamá").run(agent)
        assertFalse(result.success)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun listaYCancela() {
        val store = FakeStore()
        val alarms = FakeAlarms()
        val agent = ReminderAgent(store, alarms) { now }
        agent.say("recuérdame mañana a las 9 llamar a mamá").run(agent)
        agent.say("recuérdame el viernes a las 10 entregar el reporte").run(agent)

        val list = agent.say("¿Qué recordatorios tengo?").run(agent).reply
        assertTrue(list.contains("mañana a las 9:00: llamar a mamá"))
        assertTrue(list.contains("el viernes 16 de octubre a las 10:00: entregar el reporte"))

        val cancel = agent.say("cancela el recordatorio de mamá").run(agent)
        assertEquals("Cancelé el recordatorio: llamar a mamá.", cancel.reply)
        assertEquals(listOf(1L), alarms.cancelled)
        assertEquals(1, store.items.size)

        assertFalse(agent.say("cancela el recordatorio").run(agent).success)
        assertEquals("Cancelé el recordatorio: entregar el reporte.", agent.say("cancela todos los recordatorios").run(agent).reply)
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun recuerdaNoEsRecordatorio() {
        val agent = ReminderAgent(FakeStore(), FakeAlarms()) { now }
        assertNull(agent.say("recuerda que me gusta el café"))
        assertNull(agent.say("hola ari"))
    }
}
