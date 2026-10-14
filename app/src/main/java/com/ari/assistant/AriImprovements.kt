package com.ari.assistant

import android.content.SharedPreferences

/**
 * Ari CEO se propone mejoras: anota lo que no supo hacer (Gemma lo marca con una línea
 * "MEJORA: …", o tú dices "anota una mejora: …") y, cuando le pides "propón mejoras",
 * arma un pedido técnico para Claude, que la programa con pruebas y te manda el APK.
 * Ari no cambia su propio código: pone la idea. (La lógica no usa Android, para probarla.)
 */
object AriImprovements {
    const val TAG = "MEJORA:"
    const val MAX_NOTES = 30
    private const val MAX_CHARS = 240
    private const val PREFS_KEY = "mejoras"

    sealed class Request {
        data class Note(val text: String) : Request()
        object Propose : Request()
        object Clear : Request()
    }

    private val NOTE = Regex("^(?:anota|apunta|guarda) (?:una |esta )?(?:mejora|idea de mejora)[:,]?\\s+(.+)$|^mejora:\\s*(.+)$")
    private val PROPOSE = Regex(
        "^(?:proponme|propon|dame|haz|arma|escribe)(?: unas| las| tus)? (?:mejoras|propuestas? de mejora|propuesta para claude)$" +
            "|^que (?:mejorarias|te mejorarias|te falta|le falta a ari)$|^(?:tus |mis )?(?:mejoras|propuestas de mejora)$"
    )
    private val CLEAR = Regex("^(?:borra|olvida|limpia) (?:las |tus |todas las )?(?:mejoras|propuestas de mejora)$")

    /** La instrucción para Gemma (solo en la Ari CEO, con el dueño). */
    fun instruction(): String =
        "Si no pudiste hacer algo que te pidieron porque a la app Ari le falta una habilidad, o algo te salió mal, " +
            "escribe al final una línea aparte que empiece con \"$TAG\" y diga en pocas palabras qué habilidad le falta a Ari " +
            "o qué hay que arreglar. Máximo una por respuesta y solo si es una mejora real de la app."

    /** Las mejoras que Gemma marcó en una respuesta. */
    fun notesIn(response: String): List<String> =
        response.lines().map { it.trim().trim('*', '_', ' ') }
            .filter { it.uppercase().startsWith(TAG) }
            .map { it.substringAfter(':').trim('*', '_', ' ').trimEnd('.').take(MAX_CHARS) }
            .filter { it.length >= 5 }

    fun parse(normalized: String): Request? {
        NOTE.matchEntire(normalized)?.let { match ->
            val note = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            return if (note.length >= 5) Request.Note(note.take(MAX_CHARS)) else null
        }
        if (PROPOSE.matches(normalized)) return Request.Propose
        if (CLEAR.matches(normalized)) return Request.Clear
        return null
    }

    /** Agrega sin repetir; las más viejas se van si son demasiadas. */
    fun add(notes: List<String>, note: String): List<String> {
        val clean = simplify(note)
        if (clean.isEmpty() || notes.any { simplify(it) == clean }) return notes
        return (notes + note.take(MAX_CHARS)).takeLast(MAX_NOTES)
    }

    /** El pedido para Claude, listo para copiar y pegar en la sesión de Claude Code. */
    fun proposal(notes: List<String>, version: String, phone: String, date: String): String = buildString {
        append("Propuesta de mejora de Ari CEO\n")
        append("Versión: ").append(version).append(" · Teléfono: ").append(phone).append(" · Fecha: ").append(date).append("\n\n")
        append("Lo que no supe hacer o salió mal:\n")
        notes.forEach { append("- ").append(it).append("\n") }
        append("\nPedido para Claude: revisa estas notas, elige las que más ayuden al dueño, prográmalas con pruebas ")
        append("en la rama de trabajo, compila con el workflow y avísale cuando el APK esté listo. ")
        append("Respeta las reglas del proyecto (sin internet salvo la descarga del modelo, nada se envía solo, ")
        append("los datos del dueño no salen del teléfono). Si algo no se puede, explícale por qué en español.")
    }

    fun load(prefs: SharedPreferences): List<String> =
        prefs.getString(PREFS_KEY, null).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_NOTES)

    fun save(prefs: SharedPreferences, notes: List<String>) {
        prefs.edit().putString(PREFS_KEY, notes.joinToString("\n") { it.replace('\n', ' ') }).apply()
    }

    private fun simplify(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
}
