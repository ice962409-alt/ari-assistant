package com.ari.assistant.agents

import com.ari.assistant.AriDatabase
import com.ari.assistant.AgentTaskRecord

/**
 * Ari como CEO: recibe cada mensaje, decide qué agente debe atenderlo,
 * le delega la tarea y registra el resultado en la bitácora.
 * Si ningún agente sabe atenderlo, el mensaje va a Gemma.
 */
class AriCeo(private val database: AriDatabase) {

    data class Delegation(val agent: Agent, val task: AgentTask)

    private val team = linkedMapOf<String, Agent>()

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

    /** Encarga [message] a un agente concreto, aunque no sea el que elegiría el CEO. */
    fun delegateTo(agent: Agent, message: String): AgentResult {
        val task = agent.match(message, normalizeForAgents(message))
            ?: return AgentResult(
                "${agent.name} no entendió la tarea. Prueba con: ${agent.examples.first()}",
                success = false
            )
        return run(Delegation(agent, task))
    }

    fun run(delegation: Delegation): AgentResult {
        val (agent, task) = delegation
        val result = try {
            agent.execute(task)
        } catch (e: Exception) {
            AgentResult("${agent.name} tuvo un error: ${e.message}", success = false)
        }
        database.addAgentTask(agent.id, task.request, result.reply, result.success)
        return result
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
