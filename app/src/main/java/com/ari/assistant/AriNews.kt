package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Novedades: cuando Ari se actualiza, cuenta qué hay de nuevo y cómo se usa, con una
 * frase para probar cada cosa. También la lista de comandos de voz (sin Android, para probarlo).
 */
object AriNews {

    data class Item(val title: String, val tryThis: String, val ceoOnly: Boolean = false)

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
            Item("Contactos de recados", "La primera vez que le mandas un recado a alguien te pregunto si lo guardo; luego lo tienes en mi burbuja: tócalo y dime el recado en voz."),
        ),
        48 to listOf(Item("Recados por voz", "En mi burbuja toca un contacto de recados y dime el recado en voz.")),
        49 to listOf(Item("Envío los recados sola", "Ajustes → Recados entre Ari → Enviar los recados sola. Te explico el permiso.", ceoOnly = true)),
        50 to listOf(Item("Lee con calma", "Mientras escribo, si subes a leer el chat se queda quieto; toca \"↓ Ir al final\" para volver abajo.")),
        51 to listOf(Item("Respondo a mi nombre", "Ajustes → Responder a \"Ari\": me enseñas cómo dices mi nombre (3 veces) y, cuando salgas de la app, di \"Ari\" y te escucho.")),
        52 to listOf(Item("Me enseñas rutinas", "Di: \"cuando te diga buenos días, dime qué tengo hoy y pon música\". Luego di \"buenos días\". Para verlas: \"mis rutinas\".")),
        53 to listOf(Item("Me propongo mejoras", "Cuando algo no me sale, lo anoto. Di \"propón mejoras\" y te armo el pedido para que Claude me mejore.", ceoOnly = true)),
        54 to listOf(Item("Mi carita nueva", "Mírame arriba: pienso murmurando y muevo la boca con cada palabra que escribo o digo. Tócame para hablar en voz.")),
        56 to listOf(Item("Manejo tus apps", "Ajustes → Manejar mis apps. Luego, en cualquier app: \"toca Siguiente\", \"teclea hola\", \"desliza hacia abajo\", \"atrás\" o \"lee mi pantalla\". Antes de enviar, pagar o borrar te pregunto.", ceoOnly = true)),
        57 to listOf(Item("Te resumo tus notificaciones", "Ajustes → Leer mis notificaciones. Luego: \"¿qué notificaciones tengo?\" o \"respóndele a Juan que ya voy\" (te pregunto antes de enviar).", ceoOnly = true)),
        58 to listOf(Item("Manejo tu teléfono", "Di: \"prende la linterna\", \"sube el volumen\", \"pausa la música\", \"siguiente canción\", \"activa no molestar\", \"sube el brillo\", \"bloquea la pantalla\" o \"toma una captura\". Con Manejar mis apps, \"llama a mamá\" ya llama sola.", ceoOnly = true)),
        59 to listOf(Item("Agenda y fotos", "Ajustes → Agenda y fotos. Luego: \"agenda cita con el dentista el viernes a las 5\", \"muéstrame las fotos de ayer\", \"mi última captura\" o \"busca el archivo factura\".", ceoOnly = true)),
        60 to listOf(Item("Tu día en números", "Ajustes → Uso del teléfono y pasos. Luego: \"¿cuánto tiempo usé el teléfono hoy?\" o \"¿cuántos pasos llevo?\".", ceoOnly = true)),
        61 to listOf(Item("Ahora me llamo ARI", "Ya no soy \"Ari CEO\": soy ARI, tu extensión. Me sigues llamando igual: di \"Ari\".", ceoOnly = true)),
        62 to listOf(Item("Me adapto a tu modelo", "Toca Modelo → Elegir un archivo y carga un Qwen u otro modelo .litertlm: me adapto a él (sin vista si no la tiene).", ceoOnly = true)),
        63 to listOf(Item("Descargo Qwen yo misma", "Toca Modelo → Descargar Qwen2.5 1.5B (o Qwen3 0.6B). Lo bajo por Wi-Fi y lo integro sola.", ceoOnly = true)),
    )

    /** Las novedades entre la versión que ya conocías ([seen]) y la actual. */
    fun since(seen: Int, current: Int, vision: Boolean = true, ceo: Boolean = true): List<Item> =
        NEWS.filter { (code, _) -> code in (seen + 1)..current }.flatMap { it.second }
            .filter { vision || !it.tryThis.contains("pantalla") }
            .filter { ceo || !it.ceoOnly }

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
        Command("Cuando te diga buenos días, dime qué tengo hoy y pon música / Mis rutinas", "una rutina que me enseñas"),
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
