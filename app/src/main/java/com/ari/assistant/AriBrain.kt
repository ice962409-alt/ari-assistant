package com.ari.assistant

/**
 * El cerebro de Ari: lo que aprende de ti al platicar. Gemma marca lo nuevo con una línea
 * "APRENDÍ: …"; Ari lo guarda entre tus recuerdos (así viaja en el respaldo, como un
 * cerebro portátil) y lo usa en todas las pláticas siguientes. (Sin Android, para probarlo.)
 */
object AriBrain {

    const val TAG = "APRENDÍ:"
    /** Cómo se distinguen en tus recuerdos (los puedes ver y borrar en Memoria). */
    const val PREFIX = "(Aprendido) "
    const val MAX_LEARNED = 50
    private const val MAX_CHARS = 200

    /** La instrucción para Gemma (va en sus instrucciones de sistema, solo con el dueño). */
    fun instruction(owner: String): String =
        "Si en la plática descubres algo nuevo, útil y duradero sobre $owner (gustos, rutinas, cómo le gusta que le hables, " +
            "qué le funciona o no), escribe al final de tu respuesta una línea aparte que empiece con \"$TAG\" y lo diga " +
            "en pocas palabras. Máximo una por respuesta y solo si es nuevo; no repitas lo que ya está en sus recuerdos."

    /** Lo aprendido en una respuesta (limpio y corto). */
    fun learnedIn(response: String): List<String> =
        response.lines().map { it.trim().trim('*', '_', ' ') }
            .filter { it.uppercase().startsWith("APRENDÍ:") || it.uppercase().startsWith("APRENDI:") }
            .map { it.substringAfter(':').trim().trimEnd('.').take(MAX_CHARS) }
            .filter { it.length >= 3 }

    /** true si [note] ya está (igual, sin importar mayúsculas) entre [known]. */
    fun alreadyKnown(note: String, known: List<String>): Boolean {
        val clean = simplify(note)
        return known.any { simplify(it.removePrefix(PREFIX)) == clean }
    }

    /** La respuesta sin las líneas "APRENDÍ:", "MEJORA:" ni "HACER:" (para decirla en voz). */
    fun withoutNotes(text: String): String =
        text.lines().filterNot { val t = it.trim().trim('*', '_', ' ').uppercase(); t.startsWith("APRENDÍ:") || t.startsWith("APRENDI:") || t.startsWith("MEJORA:") || t.startsWith("HACER:") }
            .joinToString("\n")

    private fun simplify(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
}
