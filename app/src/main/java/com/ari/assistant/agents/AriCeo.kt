package com.ari.assistant.agents

import android.os.Handler
import android.os.Looper
import com.ari.assistant.AriDatabase
import com.ari.assistant.AgentTaskRecord
import java.util.concurrent.Executors

/**
 * Ari como CEO: recibe cada mensaje, decide qué agente debe atenderlo,
 * le delega la tarea y registra el resultado en la bitácora.
 * Si ningún agente sabe atenderlo, el mensaje va a Gemma.
 */
class AriCeo(private val database: AriDatabase) {

    data class Delegation(val agent: Agent, val task: AgentTask)

    private val team = linkedMapOf<String, Agent>()

    // Los agentes trabajan uno a la vez fuera del hilo principal; el
    // resultado se entrega de vuelta en el hilo principal.
    private val worker = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())

    fun hire(agent: Agent) {
        require(agent.id !in team) { "Ya hay un agente con id ${agent.id}" }
        team[agent.id] = agent
    }

    fun agents(): List<Agent> = team.values.toList()

    fun agent(id: String): Agent? = team[normalizeForAgents(id)]

    /** Elige el primer agente que sabe atender el mensaje, o null para Gemma. */
    fun route(message: String): Delegation? {
        val normalized = normalizeForAgents(message)
        for (agent in team.values) {
            val task = agent.match(message, normalized) ?: continue
            return Delegation(agent, task)
        }
        return null
    }

    /**
     * Prepara la tarea [message] para un agente concreto, aunque no sea el que
     * elegiría el CEO. Devuelve null si el agente no la entiende.
     */
    fun prepare(agent: Agent, message: String): Delegation? =
        agent.matchDelegated(message, normalizeForAgents(message))?.let { Delegation(agent, it) }

    /** Ejecuta la delegación en segundo plano y entrega el resultado en el hilo principal. */
    fun run(delegation: Delegation, onResult: (AgentResult) -> Unit) {
        val (agent, task) = delegation
        worker.execute {
            // Throwable: un error (falta de memoria, base de datos cerrada) no
            // debe tumbar la app ni dejar a Ari esperando un resultado que no llega.
            val result = try {
                agent.execute(task)
            } catch (e: Throwable) {
                AgentResult("${agent.name} tuvo un error: ${e.message ?: e.javaClass.simpleName}", success = false)
            }
            try {
                database.addAgentTask(agent.id, task.request, result.reply, result.success)
            } catch (_: Throwable) {
                // La bitácora es secundaria: si no se puede escribir, el resultado igual llega.
            }
            mainThread.post { onResult(result) }
        }
    }

    fun shutdown() {
        worker.shutdownNow()
    }

    fun recentTasks(limit: Int): List<AgentTaskRecord> = database.recentAgentTasks(limit)

    /** Informe para /agentes: el equipo, qué hace cada uno y las últimas tareas. */
    fun report(): String {
        val text = StringBuilder("Dirijo este equipo. Estos son mis agentes:")
        team.values.forEach { agent ->
            text.append("\n\n• ${agent.name} (${agent.id}): ${agent.description}")
            text.append("\n  Ejemplos: ${agent.examples.joinToString(" · ")}")
        }

        val tasks = recentTasks(REPORT_TASK_LIMIT)
        if (tasks.isNotEmpty()) {
            text.append("\n\nÚltimas tareas delegadas:")
            tasks.forEach { task ->
                val mark = if (task.success) "✓" else "✗"
                text.append("\n$mark ${task.agentId}: ${task.request}")
            }
        }

        text.append("\n\nLo demás lo respondo yo con Gemma.")
        return text.toString()
    }

    companion object {
        private const val REPORT_TASK_LIMIT = 5
    }
}
