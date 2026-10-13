package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Ari se presenta: lo que puede hacer por ti, con ejemplos para probar.
 * Lo dice la primera vez y cada vez que le preguntas "¿qué puedes hacer?".
 * Solo menciona lo que este teléfono puede hacer (sin internet, para probarlo).
 */
object AriAbilities {

    private val QUESTIONS = listOf(
        "que puedes hacer", "que sabes hacer", "que haces", "en que me puedes ayudar",
        "en que me ayudas", "como me puedes ayudar", "como me ayudas", "para que sirves",
        "que cosas puedes hacer", "que mas puedes hacer", "dime que puedes hacer", "ayuda", "/ayuda"
    )

    /** "¿Qué puedes hacer?", "ayuda", "¿para qué sirves?". */
    fun asks(text: String): Boolean {
        val clean = normalizeForAgents(text).replace(Regex("[¿?¡!.,]"), "").trim()
        return clean in QUESTIONS || clean.removePrefix("y ").trim() in QUESTIONS ||
            clean.removePrefix("oye ").trim() in QUESTIONS
    }

    /** Lo que el teléfono deja hacer: sin la vista (versión básica) Ari no ve fotos ni la pantalla. */
    data class Phone(val vision: Boolean, val voices: Boolean)

    fun intro(userName: String, phone: Phone): String = buildString {
        val hi = if (userName.isBlank()) "¡Hola! Soy Ari" else "¡Hola, $userName! Soy Ari"
        append(hi).append(", tu asistente personal. Todo lo hago aquí en tu teléfono: tus pláticas y recuerdos no salen de él.\n\n")
        append("Esto es lo que puedo hacer por ti:\n\n")
        append("**Platicar y ayudarte a pensar**: preguntas, ideas, consejos, resúmenes o escribir un mensaje.\n\n")
        append("**Recordatorios**: \"recuérdame mañana a las 9 llamar a mamá\" o \"recuérdame en 20 minutos sacar la ropa\".\n\n")
        append("**Usar tu teléfono**: \"llama a mamá\", \"manda un WhatsApp a Juan que diga que voy tarde\", ")
        append("\"pon una alarma a las 7\", \"pon música de Shakira\", \"cómo llego al centro\". Yo lo preparo y tú confirmas el último paso.\n\n")
        append("**Recordar lo que me cuentas**: \"recuerda que no como carne\", \"mi primo Luis es dentista\" y luego \"¿qué sabes de Luis?\".\n\n")
        if (phone.vision) {
            append("**Ver fotos y documentos**: toca + para mandarme una foto, un PDF o un Word, o usa Compartir → Ari desde otra app.\n\n")
        } else {
            append("**Leer documentos**: toca + para mandarme un Word o un archivo de texto.\n\n")
        }
        append("**Platicar en voz (modo live)**: toca mi carita y háblame; te sigo escuchando aunque uses otras apps.")
        if (phone.vision) append(" Ahí también puedes decirme \"mira mi pantalla\".")
        append("\n\n")
        if (phone.voices) append("**Reconocer tu voz**: en Ajustes → Reconocer voces, para saber cuándo eres tú.\n\n")
        append("**Cambiar de color**: \"ponte rosa\", \"cambia tu color a lila\" (o en Ajustes → Color de Ari).\n\n")
        append("**Avisarte**: si sales mientras trabajo, te escribo en las notificaciones y me puedes contestar ahí.\n\n")
        append("Escribe \"enséñame\" y te enseño a usarme paso a paso. Pregúntame \"¿qué puedes hacer?\" cuando quieras volver a ver esto.")
    }

    /** Versión corta para decirla en voz. */
    fun spoken(phone: Phone): String =
        "Puedo platicar contigo, ponerte recordatorios, llamar o preparar mensajes, poner alarmas y música, " +
            "y recordar lo que me cuentas" +
            (if (phone.vision) ". También veo fotos, documentos y tu pantalla si me lo pides" else "") +
            ". Todo aquí en tu teléfono. En la pantalla te dejé la lista con ejemplos."
}
