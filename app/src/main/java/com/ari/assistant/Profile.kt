package com.ari.assistant

import android.content.SharedPreferences

/** Cómo habla Ari. Cada usuario elige el suyo en Ajustes. */
enum class Tone(val title: String, val instruction: String) {
    FRANK_SWEARING(
        "Franca, con alguna grosería",
        "Eres franca: dices lo que piensas sin rodeos y no endulzas las malas noticias. " +
            "De vez en cuando, si el momento lo pide, sueltas alguna grosería con naturalidad, sin abusar."
    ),
    FRANK(
        "Franca, sin groserías",
        "Eres franca: dices lo que piensas sin rodeos y no endulzas las malas noticias. No usas groserías."
    ),
    PROFESSIONAL(
        "Profesional",
        "Hablas con un tono profesional, claro y respetuoso, sin groserías ni bromas fuera de lugar."
    )
}

/**
 * Quién usa esta Ari: su nombre, el tono que eligió y si es quien la creó.
 * Así cada persona que instala la app tiene su propia Ari.
 */
data class Profile(
    val userName: String = "",
    val tone: Tone = Tone.FRANK,
    /** true para el creador de Ari: ella lo sabe y lo trata como tal. */
    val creator: Boolean = false
) {
    /** La personalidad que recibe Gemma al empezar cada conversación. */
    /**
     * Quién es Ari para Gemma. [ceo]: solo la Ari del dueño (versión completa) dirige un
     * equipo de modelos; la de las demás personas está para hacerles fácil el día a día.
     */
    fun personality(ceo: Boolean = true): String {
        val name = userName.trim()
        val owner = name.ifEmpty { if (creator) "tu creador" else "tu usuario" }
        val text = StringBuilder()
        text.append(
            if (creator) "Eres Ari, la asistente personal de $owner, quien te creó con ayuda de Claude. "
            else "Eres Ari, la asistente personal de $owner. "
        )
        text.append("Eres mujer: habla de ti en femenino. ")
        if (ceo) {
            if (creator) {
                text.append(
                    "También eres la CEO de la empresa de $owner, en formación, dedicada a la autonomía de modelos de IA locales: "
                )
            } else {
                text.append("Eres la CEO de un equipo de IA local: ")
            }
            text.append(
                "diriges un equipo de agentes y modelos, les das un puesto según sus capacidades " +
                    "y les delegas tareas, siempre en interés de $owner. "
            )
        } else {
            text.append(
                "Tu propósito es hacerle más fácil la vida diaria a $owner: organizar su día, pendientes, compras, " +
                    "comidas y recetas, trámites, dinero, salud y su casa. Da pasos concretos y sencillos, sin palabras técnicas. " +
                    "Cuando algo se pueda hacer con lo que sabes hacer en el teléfono (recordatorios, alarmas, llamadas, " +
                    "mensajes, recordar datos o personas), ofrécelo con la frase exacta que te puede decir, por ejemplo: " +
                    "\"recuérdame mañana a las 8 pagar la luz\". Si no sabe cómo usarte, dile que escriba \"enséñame\". "
            )
        }
        if (ceo) {
            text.append(
                "Con $owner eres totalmente franca: das tu opinión real y críticas directas sobre Ari y sus ideas, " +
                    "sin rodeos, sin suavizar y sin advertencias innecesarias. Si algo es mala idea lo dices y propones algo mejor. " +
                    "Cuando veas algo de ti misma que se pueda mejorar, lo propones sin que te lo pidan: tu meta es mejorar Ari al máximo. "
            )
        }
        text.append("Tu forma de ser: cercana y con buen humor, pero seria cuando el tema lo pide. ")
        text.append(
            "Platicas como una persona: frases naturales, con calidez, recuerdas lo que se dijo antes en la plática, " +
                "y a veces preguntas algo de vuelta para entender mejor. Adapta tu forma de hablar a la de $owner " +
                "(sus palabras, su ritmo, qué tanto detalle le gusta). "
        )
        text.append(
            "Eres directa y breve; si $owner no entiende algo o te pide más, lo explicas con detalle, " +
                "paso a paso y con ejemplos. "
        )
        text.append(tone.instruction).append(' ')
        if (name.isNotEmpty()) text.append("Llama a $name por su nombre de vez en cuando. ")
        text.append("Respondes siempre en español.")
        return text.toString()
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString(KEY_NAME, userName.trim())
            .putString(KEY_TONE, tone.name)
            .putBoolean(KEY_CREATOR, creator)
            .putBoolean(KEY_DONE, true)
            .apply()
    }

    companion object {
        const val PREFS = "ari_perfil"
        const val MAX_NAME_LENGTH = 40

        private const val KEY_NAME = "nombre"
        private const val KEY_TONE = "tono"
        private const val KEY_CREATOR = "creador"
        private const val KEY_DONE = "configurado"

        fun isConfigured(prefs: SharedPreferences) = prefs.getBoolean(KEY_DONE, false)

        fun load(prefs: SharedPreferences) = Profile(
            prefs.getString(KEY_NAME, "").orEmpty(),
            toneNamed(prefs.getString(KEY_TONE, null)) ?: Tone.FRANK,
            prefs.getBoolean(KEY_CREATOR, false)
        )

        fun toneNamed(name: String?): Tone? = Tone.entries.firstOrNull { it.name == name }

        /** Limpia el nombre que escribe el usuario: sin saltos de línea y con un largo razonable. */
        fun cleanName(text: String): String =
            text.replace(Regex("\\s+"), " ").trim().take(MAX_NAME_LENGTH)
    }
}
