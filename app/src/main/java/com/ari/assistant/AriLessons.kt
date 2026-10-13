package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Ari te enseña a usarla, una cosa a la vez: te propone algo para probar, se da
 * cuenta cuando lo haces y te felicita antes de pasar a lo siguiente. Todo con
 * cosas útiles del día a día (sin Android, para probarlo).
 */
object AriLessons {

    enum class Lesson(val id: String, val title: String, val tryThis: String, val why: String) {
        CHAT(
            "platicar", "Platicar conmigo",
            "Escríbeme una pregunta de tu día, por ejemplo: \"¿qué puedo cocinar con huevo y papas?\"",
            "Puedo ayudarte a pensar, explicar cosas, escribir mensajes o planear tu día."
        ),
        REMINDER(
            "recordatorio", "Ponerte un recordatorio",
            "Escribe: \"recuérdame en 5 minutos tomar agua\"",
            "Te aviso con una notificación aunque tengas la app cerrada."
        ),
        MEMORY(
            "recuerdo", "Que recuerde algo de ti",
            "Escribe: \"recuerda que me gusta el café sin azúcar\"",
            "Así te conozco y mis respuestas son más para ti. Lo ves con \"¿qué recuerdas de mí?\"."
        ),
        PEOPLE(
            "personas", "Presentarme a alguien",
            "Escribe: \"mi hermana Laura es enfermera\"",
            "Me acuerdo de tu gente y de sus cumpleaños (\"Laura cumple el 3 de mayo\"). Luego pregúntame \"¿qué sabes de Laura?\"."
        ),
        PHONE(
            "telefono", "Usar tu teléfono",
            "Escribe: \"pon una alarma a las 7\"",
            "También llamo, preparo WhatsApp, pongo música o te digo cómo llegar. Tú confirmas el último paso."
        ),
        VOICE(
            "voz", "Hablarme en voz",
            "Toca el botón del micrófono (o mi carita) y dime: \"¿qué hora es?\"",
            "En voz te contesto hablando, y te sigo escuchando aunque uses otras apps."
        ),
        PHOTO(
            "foto", "Mandarme una foto",
            "Toca + y elige una foto, por ejemplo de un recibo o de un plato.",
            "Te digo qué hay, leo el texto o te explico un documento."
        ),
        COLOR(
            "color", "Cambiar mi color",
            "Escribe: \"ponte rosa\"",
            "Hay seis colores; vuelve al de siempre con \"ponte azul\"."
        );
    }

    /** Las lecciones que este teléfono puede hacer (sin la vista no hay foto). */
    fun available(vision: Boolean): List<Lesson> =
        Lesson.values().filter { vision || it != Lesson.PHOTO }

    fun next(done: Set<String>, vision: Boolean): Lesson? =
        available(vision).firstOrNull { it.id !in done }

    /** Qué lección cumple lo que pasó: el agente que atendió ("recordatorios", "telefono"…) o "gemma". */
    fun completedBy(handler: String): Lesson? = when (handler) {
        "gemma" -> Lesson.CHAT
        "recordatorios" -> Lesson.REMINDER
        "memoria" -> Lesson.MEMORY
        "personas" -> Lesson.PEOPLE
        "telefono" -> Lesson.PHONE
        "voz" -> Lesson.VOICE
        "foto" -> Lesson.PHOTO
        "color" -> Lesson.COLOR
        else -> null
    }

    private val ASKS = setOf(
        "ensename", "ensename a usarte", "como te uso", "como funcionas", "tutorial", "aprender",
        "ensename como funcionas", "como se usa", "como te puedo usar", "quiero aprender a usarte", "/aprender"
    )

    /** "Enséñame", "¿cómo te uso?", "tutorial". */
    fun asks(text: String): Boolean = normalizeForAgents(text).replace(Regex("[¿?¡!.,]"), "").trim() in ASKS

    /** El mensaje con el que Ari propone la siguiente lección. */
    fun prompt(lesson: Lesson, number: Int, total: Int): String =
        "**Lección $number de $total: ${lesson.title}.** ${lesson.why}\n\n→ ${lesson.tryThis}"

    fun congrats(lesson: Lesson, doneCount: Int, total: Int): String =
        "¡Muy bien! Ya sabes: ${lesson.title.replaceFirstChar { it.lowercase() }} ($doneCount de $total)."

    fun finished(): String =
        "¡Terminaste! Ya sabes usarme. Cuando quieras, pregúntame \"¿qué puedes hacer?\" para ver todo, " +
            "o simplemente dime lo que necesitas."
}
