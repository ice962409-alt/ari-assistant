package com.ari.assistant.agents


/**
 * Guarda, lista y olvida recuerdos con frases normales, sin comandos:
 * "recuerda que me gusta el café", "olvida café", "qué recuerdas de mí".
 */
class MemoryAgent(
    private val database: MemoryStore,
    private val onMemoriesChanged: () -> Unit
) : Agent {

    override val id = "memoria"
    override val name = "Agente de memoria"
    override val description = "Guarda, muestra y olvida lo que Ari sabe de ti."
    override val examples = listOf(
        "recuerda que me gusta el café",
        "qué recuerdas de mí",
        "olvida café"
    )

    override fun match(message: String, normalized: String): AgentTask? {
        if (LIST_PATTERN.matches(normalized)) {
            return AgentTask(message, ACTION_LIST)
        }

        REMEMBER_PATTERN.matchEntire(message.trim())?.let { found ->
            val content = cleanArgument(found.groupValues[1].ifEmpty { found.groupValues[2] })
            if (content.isNotEmpty()) return AgentTask(message, ACTION_REMEMBER, content)
        }

        FORGET_PATTERN.matchEntire(message.trim())?.let { found ->
            val content = cleanArgument(found.groupValues[1])
            // "olvida eso", "olvida lo que te dije": es conversación, no una orden
            // de borrar; se lo deja a Gemma en lugar de borrar recuerdos a ciegas.
            val vague = VAGUE_FORGET.containsMatchIn(normalizeForAgents(content))
            if (content.length >= MIN_FORGET_LENGTH && !vague) {
                return AgentTask(message, ACTION_FORGET, content)
            }
        }

        return null
    }

    override fun execute(task: AgentTask): AgentResult = when (task.action) {
        ACTION_REMEMBER -> {
            database.addMemory(task.argument)
            onMemoriesChanged()
            AgentResult("Lo recordaré: ${task.argument}")
        }

        ACTION_FORGET -> {
            val deleted = database.deleteMemoriesMatching(task.argument)
            if (deleted > 0) onMemoriesChanged()
            when (deleted) {
                0 -> AgentResult(
                    "No encontré recuerdos que contengan \"${task.argument}\".",
                    success = false
                )
                1 -> AgentResult("Olvidé 1 recuerdo.")
                else -> AgentResult("Olvidé $deleted recuerdos.")
            }
        }

        ACTION_LIST -> {
            val memories = database.memories()
            if (memories.isEmpty()) {
                AgentResult("Aún no tengo recuerdos guardados.")
            } else {
                AgentResult(
                    "Esto es lo que recuerdo:" +
                        memories.joinToString("") { "\n• ${it.content}" }
                )
            }
        }

        else -> AgentResult("Acción desconocida: ${task.action}", success = false)
    }

    private fun cleanArgument(text: String): String =
        text.trim().trimEnd('.', '!', '?', ' ')

    companion object {
        private const val ACTION_REMEMBER = "recordar"
        private const val ACTION_FORGET = "olvidar"
        private const val ACTION_LIST = "listar"

        private const val PREFIX = "^[¿¡\\s]*(?:ari[,:]?\\s+)?"

        // "recuerda (que) X" o "guarda/anota que X". "Recuérdame" no entra:
        // suele pedir un aviso, no guardar un dato.
        private val REMEMBER_PATTERN = Regex(
            "$PREFIX(?:recuerda(?:\\s+que)?\\s+(.+)|(?:guarda|anota)\\s+que\\s+(.+))$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        private val FORGET_PATTERN = Regex(
            "${PREFIX}olvida(?:\\s+que)?\\s+(.+)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        private const val MIN_FORGET_LENGTH = 3

        // Argumentos de "olvida" que no nombran un recuerdo concreto.
        private val VAGUE_FORGET = Regex(
            "^(?:eso|esto|aquello|lo (?:que|de|anterior|ultimo)|todo|que te dije)\\b"
        )

        // Se compara con el texto normalizado (sin acentos ni signos).
        private val LIST_PATTERN = Regex(
            "^(?:que (?:recuerdas|sabes)(?: de mi)?|mis recuerdos|muestra(?:me)? (?:tus|mis) recuerdos)$"
        )
    }
}
