package com.ari.assistant.agents

/**
 * Arregla lo que el reconocimiento de voz (o el teclado) suele traer antes de buscar la orden:
 * - "Ari" mal escuchado al principio ("París abre WhatsApp", "Harry pon una alarma").
 * - "Hacer: …" copiado de los pasos de Gemma.
 * - Dos órdenes en una: "abre WhatsApp y mándale un mensaje a mi niña".
 * Solo cambia la frase si lo que queda empieza como una orden o una pregunta; si no, la deja igual.
 * Sin Android, para probarlo.
 */
object SpokenCommand {

    /** Cómo suele escribir el reconocedor "Ari" (y "oye Ari") al principio de una frase. */
    private val WAKE = Regex(
        "^(?:(?:oye|hey|hola|ey|oiga)\\s+)?(?:ari|aris|ary|arik|hari|haris|harry|hary|paris)\\b[\\s,.:;!¡¿?]*"
    )

    /** "Hacer: mándale…" (la forma de los pasos de Gemma). */
    private val DO = Regex("^hacer\\s*:\\s*")

    /** Primeras palabras de una orden o pregunta (en minúsculas y sin acentos). */
    private val COMMAND_START = Regex(
        "^(?:abre|abreme|abrir|manda|mandale|mandame|envia|enviale|escribe|escribele|dile|avisale|llama|llamale|marca|" +
            "pon|ponme|prende|apaga|enciende|activa|desactiva|sube|baja|busca|buscame|recuerdame|recuerda|olvida|agenda|" +
            "anota|toma|saca|reproduce|toca|pausa|para|deten|siguiente|lleva|llevame|pide|pideme|cierra|lee|muestrame|" +
            "que|cual|cuales|cuando|cuanto|cuanta|cuantos|como|donde|quien|dime|cuentame|hay|game|modo|voy|prepara)\\b"
    )

    /** "abre WhatsApp y …" (o Telegram): lo que sigue es el mensaje. */
    private val OPEN_AND = Regex(
        "^(?:abre|abreme|abrir)\\s+(whatsapp|whatsap|watsapp|watsap|whats|wasap|wassap|guasap|telegram)\\s*,?\\s+y\\s+(.+)$"
    )

    /** Verbos de mandar un mensaje. */
    private val MESSAGE_VERB = Regex("^(?:manda|mandale|envia|enviale|escribe|escribele|dile|avisale)\\b")

    /** Ya dice por dónde. */
    private val CHANNEL = Regex("\\b(?:whatsapp|whatsap|watsapp|watsap|whats|wasap|wassap|wasa|wats|guasap|guasa|telegram|sms)\\b")

    /** Dónde empieza el texto del mensaje: ahí se mete "por WhatsApp". */
    private val SAYING = Regex("\\s+(?:que\\s+le\\s+digas?|que\\s+diga|diciendo|que)\\s+")

    fun clean(spoken: String): String {
        var text = spoken.trim()
        var plain = ReminderParser.plain(text)
        WAKE.find(plain)?.let { found ->
            val rest = text.substring(found.range.last + 1).trim()
            if (rest.isNotEmpty() && COMMAND_START.containsMatchIn(ReminderParser.plain(rest))) text = rest
        }
        plain = ReminderParser.plain(text)
        DO.find(plain)?.let { found ->
            val rest = text.substring(found.range.last + 1).trim()
            if (rest.isNotEmpty()) text = rest
        }
        plain = ReminderParser.plain(text)
        OPEN_AND.matchEntire(plain)?.let { found ->
            val restStart = found.groups[2]!!.range.first
            val rest = text.substring(restStart).trim()
            val restPlain = plain.substring(restStart).trim()
            if (MESSAGE_VERB.containsMatchIn(restPlain)) {
                // Si no dice por dónde, es por la app que pidió abrir.
                val channel = if (found.groupValues[1] == "telegram") "Telegram" else "WhatsApp"
                text = if (CHANNEL.containsMatchIn(restPlain)) rest else withChannel(rest, restPlain, channel)
            }
        }
        return text
    }

    /** "mándale un mensaje a mi niña que diga hola" → "mándale un mensaje a mi niña por WhatsApp que diga hola". */
    private fun withChannel(message: String, plain: String, channel: String): String {
        val saying = SAYING.find(plain) ?: return "${message.trimEnd('.', '!', ' ')} por $channel"
        return message.substring(0, saying.range.first) + " por $channel" + message.substring(saying.range.first)
    }
}
