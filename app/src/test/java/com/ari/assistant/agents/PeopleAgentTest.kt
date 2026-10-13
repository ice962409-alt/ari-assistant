package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeopleAgentTest {

    private class FakeStore : PeopleStore {
        val saved = mutableListOf<Person>()
        private var next = 1L
        override fun people() = saved.toList()
        override fun savePerson(person: Person): Long {
            if (person.id == 0L) {
                val created = person.copy(id = next++)
                saved.add(created)
                return created.id
            }
            saved.replaceAll { if (it.id == person.id) person else it }
            return person.id
        }
        override fun deletePerson(id: Long) {
            saved.removeAll { it.id == id }
        }
    }

    private val store = FakeStore()
    private var changes = 0
    private val agent = PeopleAgent(store, { changes++ }, { LocalDate.of(2026, 10, 13) })

    private fun say(text: String): AgentResult? {
        val task = agent.match(text, normalizeForAgents(text)) ?: return null
        return agent.execute(task)
    }

    @Test
    fun recuerdaAQuienLeMencionas() {
        assertEquals("Anotado: Luis (tu primo) es dentista.", say("mi primo Luis es dentista")?.reply)
        val luis = store.saved.single()
        assertEquals("Luis", luis.name)
        assertEquals("primo", luis.relation)
        assertEquals(listOf("es dentista"), luis.facts)
        assertEquals(1, changes)
    }

    @Test
    fun juntaLoQueSabeDeLaMismaPersona() {
        say("mi primo Luis es dentista")
        say("Luis cumple el 5 de mayo")
        say("recuerda que mi primo Luis vive en Puebla")
        val luis = store.saved.single()
        assertEquals(listOf("es dentista", "vive en Puebla"), luis.facts)
        assertEquals("05-05", luis.birthday)
        val reply = say("qué sabes de Luis")!!.reply
        assertTrue(reply, reply.contains("Luis es tu primo"))
        assertTrue(reply, reply.contains("es dentista; vive en Puebla"))
        assertTrue(reply, reply.contains("5 de mayo"))
    }

    @Test
    fun laMamaSinNombreLuegoConNombre() {
        say("mi mamá es doctora")
        assertEquals("Mamá", store.saved.single().name)
        say("mi mamá se llama Rosa")
        val rosa = store.saved.single()
        assertEquals("Rosa", rosa.name)
        assertEquals("mamá", rosa.relation)
        assertEquals(listOf("es doctora"), rosa.facts)
    }

    @Test
    fun otrasFormasDePresentar() {
        say("Ana es mi jefa")
        say("te presento a Pedro, mi vecino")
        assertEquals(listOf("Ana" to "jefa", "Pedro" to "vecino"), store.saved.map { it.name to it.relation })
    }

    @Test
    fun preguntasYConsejosSonParaGemma() {
        assertNull(say("mi jefe es muy pesado, ¿qué hago?"))
        assertNull(say("mi novia es celosa pero la quiero"))
        assertNull(say("quién es Einstein"))
        assertNull(say("olvida a Juan"))
        assertNull(say("hoy es mi cumpleaños"))
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun olvidaYLista() {
        say("mi primo Luis es dentista")
        say("Ana es mi jefa")
        assertTrue(say("a quién conoces")!!.reply.contains("Luis (primo): es dentista"))
        assertEquals("Listo, olvidé a Luis.", say("olvida a Luis")?.reply)
        assertEquals(listOf("Ana"), store.saved.map { it.name })
    }

    @Test
    fun proximosCumpleanos() {
        say("Luis cumple el 14 de octubre")
        say("Ana cumple el 1 de enero")
        val reply = say("quién cumple años")!!.reply
        assertTrue(reply, reply.indexOf("Luis") < reply.indexOf("Ana"))
        assertTrue(reply, reply.contains("14 de octubre (mañana)"))
    }
}
