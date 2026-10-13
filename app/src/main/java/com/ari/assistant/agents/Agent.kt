package com.ari.assistant.agents

import java.text.Normalizer

/** Una tarea concreta que el CEO encarga a un agente. */
data class AgentTask(
    val request: String,
    val action: String,
    val argument: String = "",
    /** true si Ari la delegó por su cuenta (sin que el usuario nombrara al agente). */
    val automatic: Boolean = false
)

data class AgentResult(val reply: String, val success: Boolean = true)

/**
 * Contrato que cumple cualquier agente o bot que trabaje para Ari.
 *
 * Los agentes de hoy son locales, pero el contrato está pensado para que en
 * el futuro también lo cumplan bots externos (por ejemplo, un servicio en la
 * red) sin cambiar al CEO.
 */
interface Agent {
    /** Identificador corto y estable, por ejemplo "memoria". */
    val id: String

    val name: String

    /** Qué sabe hacer, en una frase. El CEO lo muestra en /agentes. */
    val description: String

    /** Ejemplos de lo que el usuario puede decir para usar este agente. */
    val examples: List<String>

    /**
     * Si el agente sabe atender [message], devuelve la tarea a ejecutar;
     * si no, null. [normalized] es el mensaje en minúsculas y sin acentos.
     */
    fun match(message: String, normalized: String): AgentTask?

    /**
     * Como [match], pero para tareas que el usuario encargó a este agente
     * por su nombre (/delega). Un agente puede aceptar aquí más cosas.
     */
    fun matchDelegated(message: String, normalized: String): AgentTask? =
        match(message, normalized)

    /**
     * Ejecuta la tarea. El CEO la llama en un hilo en segundo plano, así que
     * puede tardar (por ejemplo, esperar a un modelo en otra computadora).
     */
    fun execute(task: AgentTask): AgentResult
}

/** Minúsculas, sin acentos, sin signos al principio y al final, y sin "Ari," delante. */
fun normalizeForAgents(text: String): String {
    val withoutAccents = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return trimPunctuation(withoutAccents)
        .replace(Regex("^ari[,:]?\\s+"), "")
        .let(::trimPunctuation)
        .replace(Regex("\\s+"), " ")
}

private fun trimPunctuation(text: String): String =
    text.trim('¿', '¡', '?', '!', '.', ',', ' ', '\n', '\t')
