package com.ari.assistant.agents

/**
 * Puestos que Ari asigna a los modelos de su equipo según lo que cada uno
 * sabe hacer. La empresa se dedica a la autonomía de modelos de IA locales,
 * así que los puestos están pensados para construir y hacer crecer a Ari.
 */
enum class Role(
    val title: String,
    val duty: String,
    /** Palabras (sin acentos) que hacen que Ari le mande el mensaje a este puesto. */
    val keywords: List<String>
) {
    DEVELOPMENT(
        "Desarrollo de software",
        "Escribe, revisa y explica código, y ayuda a construir a Ari y las herramientas de la empresa.",
        listOf(
            "codigo", "programar", "programacion", "kotlin", "python",
            "java", "javascript", "script", "compilar", "compilacion", "bug", "api", "linux", "terminal"
        )
    ),
    ANALYSIS(
        "Análisis y estrategia",
        "Investiga, razona a fondo y redacta planes, estrategias y documentos para la empresa.",
        listOf(
            "analiza", "analisis", "estrategia", "plan de", "planea", "investiga", "compara",
            "redacta", "propuesta", "ventajas y desventajas", "pros y contras"
        )
    ),
    GENERAL(
        "Asistencia general",
        "Atiende las tareas variadas que Ari le encarga.",
        emptyList()
    ),
    QUICK(
        "Respuestas rápidas",
        "Contesta preguntas cortas al instante.",
        emptyList()
    );

    companion object {
        /** Orden de preferencia cuando la tarea no es de ninguna especialidad. */
        val GENERAL_PREFERENCE = listOf(ANALYSIS, GENERAL, QUICK, DEVELOPMENT)

        /**
         * Asigna el puesto según el nombre del modelo: los especializados en
         * código van a desarrollo; los grandes (7B o más) a análisis; los
         * pequeños (menos de 4B) a respuestas rápidas. Devuelve null para los
         * modelos que no conversan (embeddings).
         */
        fun assign(model: String): Role? {
            val name = model.lowercase()
            if (EMBEDDING_HINTS.any { it in name }) return null
            if (CODE_HINTS.any { it in name }) return DEVELOPMENT

            val billions = SIZE_PATTERN.find(name)?.groupValues?.get(1)?.toDoubleOrNull()
            return when {
                billions == null -> GENERAL
                billions >= 7 -> ANALYSIS
                billions < 4 -> QUICK
                else -> GENERAL
            }
        }

        /** Puesto especializado que pide el mensaje, si lo hay. [normalized] va sin acentos. */
        fun requestedBy(normalized: String): Role? =
            listOf(DEVELOPMENT, ANALYSIS).firstOrNull { role ->
                role.keywords.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(normalized) }
            }

        private val EMBEDDING_HINTS = listOf("embed", "bge-", "minilm", "nomic-embed")
        private val CODE_HINTS = listOf("coder", "code", "starcoder", "codellama", "devstral", "codestral")

        // "llama3.1:8b", "qwen2.5-7b-instruct", "gemma-3-4b-it", "phi-3.5-mini-3.8b"...
        private val SIZE_PATTERN = Regex("(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*b(?![a-z])")
    }
}
