package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * "Configurar Ari": una sola lista con los pasos para dejar a Ari lista, palomeados
 * según lo que ya hiciste. Cada paso dice para qué sirve en una frase.
 * Sin Android, para poder probarlo; MainActivity revisa cada paso y lo abre.
 */
object SetupGuide {

    enum class Step(val title: String, val why: String, val essential: Boolean) {
        NAME("Tu nombre", "para saludarte y adaptarse a ti", true),
        MODEL("Su cerebro (el modelo)", "para pensar y platicar sin internet", true),
        NOTIFICATIONS("Notificaciones", "para avisarte de recordatorios y alarmas", true),
        MICROPHONE("Micrófono", "para hablar con ella en voz", true),
        CONTACTS("Contactos", "para llamar o mandar recados por nombre", false),
        CALENDAR("Calendario", "para decirte tu agenda", false),
        BUBBLE("Burbuja flotante", "para llamarla desde cualquier app", false),
        WAKE_WORD("Responder a su nombre", "para decir \"Ari\" y que te escuche", false),
        LESSONS("Aprender a usarla", "lecciones cortas para sacarle provecho", false),
    }

    /** Una línea por paso: "✓ Tu nombre" o "○ Micrófono — para hablar con ella en voz". */
    fun lines(done: Set<Step>): List<String> = Step.values().map { step ->
        if (step in done) "✓ ${step.title}" else "○ ${step.title} — ${step.why}"
    }

    fun progress(done: Set<Step>): String {
        val total = Step.values().size
        val count = Step.values().count { it in done }
        val essentials = Step.values().filter { it.essential }
        return when {
            count == total -> "¡Todo listo! ($count de $total)"
            essentials.all { it in done } -> "Lo esencial está listo ($count de $total). Lo demás es opcional."
            else -> "Llevas $count de $total. Toca un paso para hacerlo."
        }
    }

    /** true si ya está lo esencial (para no insistir de más). */
    fun essentialsDone(done: Set<Step>) = Step.values().filter { it.essential }.all { it in done }

    private val ASK = Regex("^(?:configurame|configura(?:r)? (?:a )?ari|configuracion(?: de ari)?|ayudame a configurarte|como te configuro|asistente de configuracion)$")

    fun asks(text: String) = ASK.matches(normalizeForAgents(text))
}
