package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Novedades: cuando Ari se actualiza, cuenta qué hay de nuevo y cómo se usa, con una
 * frase para probar cada cosa. También la lista de comandos de voz (sin Android, para probarlo).
 */
object AriNews {

    data class Item(val title: String, val tryThis: String)

    /** Lo nuevo de cada versión (código de versión → novedades). */
    private val NEWS: List<Pair<Int, List<Item>>> = listOf(
        37 to listOf(Item("Soy tu tutora de lo que quieras", "Di: \"quiero aprender inglés\". Para salir: \"terminar clase\".")),
        38 to listOf(Item("Estoy protegida contra copias modificadas", "No tienes que hacer nada: si alguien me altera, no abro.")),
        39 to listOf(Item("Recados a otras Ari", "Di: \"dile a mi mamá que llego a las 8\" y toca enviar en tu app de mensajes.")),
        40 to listOf(
            Item("Te digo tu agenda", "Pregunta: \"¿qué tengo hoy?\" o \"¿qué tengo mañana?\"."),
            Item("Comandos de voz", "Pregunta: \"¿qué te puedo decir?\" y te enseño las frases que entiendo."),
            Item("Novedades", "Cada vez que me actualice te cuento lo nuevo, como ahora."),
        ),
        41 to listOf(
            Item("Aprendo de ti", "Mientras platicamos, guardo lo que aprendo de ti (lo ves en Memoria como \"(Aprendido)\") y me adapto a tu forma de hablar."),
            Item("Platico más natural", "Háblame como a una persona; te pregunto de vuelta cuando hace falta."),
            Item("Te aviso en voz", "Si sales de la app mientras trabajo, te digo en voz cuando termino (Ajustes → Avisarme en voz)."),
        ),
        42 to listOf(
            Item("Burbuja flotante", "Ajustes → Burbuja flotante: mi carita flota sobre tus apps; tócala y se abre mi tarjeta para escribirme o hablarme. Te explico cómo dar el permiso."),
            Item("Interrúmpeme hablando", "En modo live, si me hablas mientras hablo, me callo y te escucho (Ajustes → Interrumpirla hablando)."),
        ),
        46 to listOf(
            Item("Leo sola los recados de otras Ari", "Ajustes → Recados entre Ari → Activar. Te explico cómo dar el acceso a notificaciones."),
        ),
        47 to listOf(
            Item("Contactos de recados", "La primera vez que le mandas un recado a alguien te pregunto si lo guardo; luego lo tienes a mano en mi burbuja: tócalo, escribe y Enviar."),
        ),
    )

    /** Las novedades entre la versión que ya conocías ([seen]) y la actual. */
    fun since(seen: Int, current: Int, vision: Boolean = true): List<Item> =
        NEWS.filter { (code, _) -> code in (seen + 1)..current }.flatMap { it.second }
            .filter { vision || !it.tryThis.contains("pantalla") }

    fun message(items: List<Item>, version: String): String = buildString {
        append("**Novedades de Ari $version.** Esto es lo nuevo que puedo hacer:\n\n")
        items.forEach { append("• **").append(it.title).append("**. ").append(it.tryThis).append("\n") }
        append("\nPregúntame \"¿qué puedes hacer?\" para ver todo.")
    }

    // --- Comandos de voz: las frases que Ari entiende siempre igual ---

    data class Command(val phrase: String, val does: String)

    val VOICE_COMMANDS = listOf(
        Command("¿Qué hora es? / ¿Qué día es hoy?", "la hora y la fecha"),
        Command("¿Qué tengo hoy? / ¿Qué tengo mañana?", "tu agenda"),
        Command("Pon una alarma a las 7", "una alarma"),
        Command("Pon un temporizador de 10 minutos", "un temporizador"),
        Command("Recuérdame mañana a las 9 llamar a mamá", "un recordatorio"),
        Command("Llama a mamá", "deja marcado el número"),
        Command("Manda un WhatsApp a Juan que diga ya voy", "el mensaje listo para enviar"),
        Command("Dile a mi mamá que llego a las 8", "un recado a otra Ari"),
        Command("Pon música de Shakira", "música"),
        Command("Cómo llego al centro", "la ruta en el mapa"),
        Command("Recuerda que no como carne", "lo guardo sobre ti"),
        Command("Mi primo Luis es dentista / ¿Qué sabes de Luis?", "tu libreta de personas"),
        Command("Mira mi pantalla", "veo tu pantalla (en modo live)"),
        Command("Quiero aprender inglés / Terminar clase", "clases conmigo"),
        Command("Ponte rosa", "cambio de color"),
        Command("¿Qué puedes hacer? / Enséñame", "te enseño a usarme"),
    )

    private val ASKS = setOf(
        "comandos", "comandos de voz", "que te puedo decir", "que comandos tienes", "que comandos hay",
        "que frases entiendes", "que te digo", "dime tus comandos", "/comandos"
    )

    fun asksCommands(text: String): Boolean = normalizeForAgents(text).replace(Regex("[¿?¡!.,]"), "").trim() in ASKS

    fun commandsMessage(): String = buildString {
        append("**Comandos de voz.** Dímelos así en el modo live (o escríbelos) y lo hago al momento:\n\n")
        VOICE_COMMANDS.forEach { append("• \"").append(it.phrase).append("\": ").append(it.does).append("\n") }
        append("\nTambién puedes hablarme normal: si no es un comando, lo platicamos.")
    }

    fun commandsSpoken(): String =
        "Algunas frases: qué hora es, qué tengo hoy, pon una alarma, recuérdame algo, llama a alguien, " +
            "dile a mi mamá que, mira mi pantalla y quiero aprender inglés. Te dejé la lista completa en la pantalla."
}
