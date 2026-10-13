package com.ari.assistant.agents

import com.ari.assistant.Memory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Reglas con las que Ari decide qué agente atiende cada mensaje. */
class AgentRulesTest {

    /** Memoria de mentira: guarda los recuerdos en una lista. */
    private class FakeStore(vararg initial: String) : MemoryStore {
        val items = initial.toMutableList()
        override fun addMemory(content: String): Long {
            items.add(content)
            return items.size.toLong()
        }
        override fun memories() = items.mapIndexed { index, content -> Memory(index.toLong(), content) }
        override fun deleteMemoriesMatching(text: String): Int {
            val before = items.size
            items.removeAll { it.contains(text, ignoreCase = true) }
            return before - items.size
        }
    }

    private fun memoryAgent(store: MemoryStore = FakeStore()) = MemoryAgent(store) {}

    private fun MemoryAgent.task(message: String) = match(message, normalizeForAgents(message))

    @Test
    fun normalizaAcentosSignosYNombre() {
        assertEquals("que hora es", normalizeForAgents("¿Ari, qué hora es?"))
        assertEquals("que dia es hoy", normalizeForAgents("  ¿Qué   día es HOY?! "))
    }

    @Test
    fun recuerdaGuardaElTexto() {
        val task = memoryAgent().task("Recuerda que me gusta el café.")
        assertEquals("recordar", task?.action)
        assertEquals("me gusta el café", task?.argument)
        assertEquals("recordar", memoryAgent().task("anota que mañana hay junta")?.action)
    }

    @Test
    fun recuerdameNoEsGuardarUnRecuerdo() {
        assertNull(memoryAgent().task("recuérdame mañana llamar a mamá"))
    }

    @Test
    fun olvidaVagoNoBorraNada() {
        val agent = memoryAgent()
        listOf("olvida eso", "olvida lo que te dije", "olvida todo", "olvida esto", "olvida yo").forEach {
            assertNull("\"$it\" no debe borrar recuerdos", agent.task(it))
        }
    }

    @Test
    fun olvidaConcretoBorraSoloEso() {
        val store = FakeStore("me gusta el café", "vivo en Monterrey")
        val agent = memoryAgent(store)
        val task = agent.task("olvida café")
        assertEquals("olvidar", task?.action)
        val result = agent.execute(task!!)
        assertTrue(result.success)
        assertEquals(listOf("vivo en Monterrey"), store.items)
    }

    @Test
    fun queRecuerdasListaLosRecuerdos() {
        val agent = memoryAgent(FakeStore("me gusta el café"))
        val task = agent.task("¿Qué recuerdas de mí?")
        assertEquals("listar", task?.action)
        assertTrue(agent.execute(task!!).reply.contains("me gusta el café"))
    }

    @Test
    fun conversacionNormalVaAGemma() {
        listOf("hola ari", "cuéntame un chiste", "qué opinas del café").forEach {
            assertNull(memoryAgent().task(it))
        }
    }

    @Test
    fun relojDiceLaHoraEnEspanol() {
        val clock = ClockAgent { LocalDateTime.of(2026, 10, 13, 9, 5) }
        val task = clock.match("¿Qué hora es?", normalizeForAgents("¿Qué hora es?"))
        assertEquals("Son las 9:05.", clock.execute(task!!).reply)

        val one = ClockAgent { LocalDateTime.of(2026, 10, 13, 1, 0) }
        assertEquals("Es la 1:00.", one.execute(task).reply)
    }

    @Test
    fun relojDiceLaFecha() {
        val clock = ClockAgent { LocalDateTime.of(2026, 10, 13, 9, 5) }
        val task = clock.match("qué día es hoy", normalizeForAgents("qué día es hoy"))
        assertEquals("Hoy es martes 13 de octubre de 2026.", clock.execute(task!!).reply)
        assertNull(clock.match("qué hora es en Tokio", normalizeForAgents("qué hora es en Tokio")))
    }

    @Test
    fun puestoSegunElModelo() {
        assertEquals(Role.ANALYSIS, Role.assign("llama3.1:8b"))
        assertEquals(Role.DEVELOPMENT, Role.assign("qwen2.5-coder:7b"))
        assertEquals(Role.QUICK, Role.assign("llama3.2:3b"))
        assertEquals(Role.QUICK, Role.assign("phi-3.5-mini-3.8b"))
        assertEquals(Role.ANALYSIS, Role.assign("qwen2.5-7b-instruct"))
        assertEquals(Role.GENERAL, Role.assign("gemma-3-4b-it"))
        assertEquals(Role.GENERAL, Role.assign("mistral"))
        assertNull(Role.assign("nomic-embed-text"))
    }

    @Test
    fun especialidadQuePideElMensaje() {
        assertEquals(Role.DEVELOPMENT, Role.requestedBy(normalizeForAgents("Escribe un script en Python")))
        assertEquals(Role.ANALYSIS, Role.requestedBy(normalizeForAgents("Analiza los pros y contras")))
        assertNull(Role.requestedBy(normalizeForAgents("hola, ¿cómo estás?")))
        // "apis" no es "api": las palabras se comparan completas.
        assertNull(Role.requestedBy("me gustan las apisonadoras"))
    }

    @Test
    fun soloDireccionesPrivadas() {
        listOf("192.168.1.50", "10.0.0.2", "172.16.0.1", "172.31.255.255").forEach {
            assertTrue(it, NetworkGuard.isPrivateAddress(it))
        }
        listOf("8.8.8.8", "172.32.0.1", "192.169.0.1", "localhost", "example.com", "192.168.1", "300.1.1.1").forEach {
            assertFalse(it, NetworkGuard.isPrivateAddress(it))
        }
    }
}
