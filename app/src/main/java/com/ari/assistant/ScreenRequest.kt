package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Reconoce cuando le pides a Ari que mire tu pantalla: "mira mi pantalla",
 * "¿qué hay en la pantalla?", "lee lo que dice mi pantalla".
 *
 * No cuenta lo que es sobre la pantalla misma ("baja el brillo de la pantalla",
 * "cambia el fondo de pantalla"): eso no necesita que Ari la vea.
 */
object ScreenRequest {

    private val VERBS = setOf(
        "mira", "mirar", "miras", "mires", "ve", "ver", "ves", "veas", "viendo",
        "checa", "checar", "checas", "revisa", "revisar", "revises", "lee", "leer", "lees", "leas",
        "dice", "dicen", "hay", "tengo", "sale", "salen", "aparece", "aparecen",
        "describe", "describeme", "explica", "explicame", "analiza", "ayudame", "observa", "fijate"
    )

    private val NOT_LOOKING = setOf(
        "brillo", "apaga", "apagar", "apagues", "bloquea", "bloquear", "enciende", "prende",
        "fondo", "gira", "girar", "rota", "rotar", "tiempo", "graba", "grabar", "grabacion", "completa"
    )

    fun matches(text: String): Boolean {
        val words = normalizeForAgents(text).split(Regex("[^a-z0-9ñ]+")).filter { it.isNotEmpty() }
        val screen = words.indexOfFirst { it == "pantalla" || it == "pantallita" }
        if (screen < 0) return false
        if (words.any { it in NOT_LOOKING }) return false
        // "mi pantalla", "la pantalla", "en pantalla", "esta pantalla".
        val before = words.getOrNull(screen - 1)
        if (before !in setOf("mi", "la", "en", "esta", "esa", "tu")) return false
        return words.any { it in VERBS }
    }
}
