package com.ari.assistant.agents

/** Una acción en el teléfono que Ari puede hacer por ti. */
sealed class PhoneAction {
    data class OpenApp(val name: String) : PhoneAction()
    data class Dial(val number: String) : PhoneAction()
    /** [number] vacío: WhatsApp pregunta a quién enviarlo. */
    data class WhatsApp(val number: String, val text: String) : PhoneAction()
    data class Alarm(val hour: Int, val minute: Int, val label: String) : PhoneAction()
    data class Timer(val seconds: Int, val label: String) : PhoneAction()
    data class WebSearch(val query: String) : PhoneAction()
    data class Navigate(val place: String) : PhoneAction()
    object Camera : PhoneAction()
    data class Settings(val section: String) : PhoneAction()
    /** La frase pedía una acción, pero falta algo; [message] explica qué. */
    data class Missing(val message: String) : PhoneAction()
}

data class InstalledApp(val label: String, val packageName: String)

/**
 * Entiende órdenes como "abre WhatsApp", "llama al 55 1234 5678",
 * "manda un WhatsApp al 5512345678 que diga ya voy", "pon una alarma a las 7",
 * "pon un temporizador de 10 minutos", "busca en Google recetas de pozole",
 * "cómo llego al aeropuerto". Sin Android, para poder probarlo.
 */
object PhoneCommands {

    fun parse(message: String): PhoneAction? {
        val original = message.trim()
        // Misma longitud que el original, para recortar el texto real (con acentos y mayúsculas).
        val plain = ReminderParser.plain(original)
        val start = POLITE.find(plain)?.range?.last?.plus(1) ?: 0
        val p = plain.substring(start).trimEnd('.', '!', '?', ' ')
        val o = original.substring(start).trimEnd('.', '!', '?', ' ')

        CAMERA.matchEntire(p)?.let { return PhoneAction.Camera }

        SETTINGS.matchEntire(p)?.let { found ->
            return PhoneAction.Settings(found.groupValues[1].ifEmpty { "general" })
        }

        WHATSAPP.find(p)?.let { found ->
            if (found.range.first != 0) return@let
            val rest = o.substring(found.range.last + 1)
            val restPlain = p.substring(found.range.last + 1)
            val number = NUMBER.find(restPlain)
            var textStart = number?.range?.last?.plus(1) ?: 0
            SAYING.find(restPlain, textStart)?.let { textStart = it.range.last + 1 }
            val text = rest.substring(textStart).trim().trim(':', ',', ' ')
            if (text.isEmpty()) {
                return PhoneAction.Missing("¿Qué le escribo? Dime, por ejemplo: manda un WhatsApp al 55 1234 5678 que diga ya voy.")
            }
            return PhoneAction.WhatsApp(number?.value?.let(::digits).orEmpty(), text)
        }

        DIAL.matchEntire(p)?.let { found ->
            val target = found.groupValues[1].trim()
            val number = NUMBER.matchEntire(target)
            // "llama la atención que…" es conversación, no una llamada.
            if (number == null && target.split(" ").size > 3) return null
            return if (number != null) PhoneAction.Dial(digits(number.value))
            else PhoneAction.Missing("Todavía no puedo buscar en tus contactos. Dime el número: llama al 55 1234 5678.")
        }

        ALARM.matchEntire(p)?.let { found -> return alarm(found, o) }

        TIMER.matchEntire(p)?.let { found ->
            val amount = when (val word = found.groupValues[1]) {
                "un", "una" -> 1
                "media" -> 0
                else -> word.toIntOrNull() ?: return null
            }
            val unit = found.groupValues[2]
            val seconds = when {
                found.groupValues[1] == "media" && unit.startsWith("h") -> 30 * 60
                found.groupValues[1] == "media" -> 30
                unit.startsWith("h") -> amount * 3600
                unit.startsWith("s") -> amount
                else -> amount * 60
            }
            if (seconds !in 1..(24 * 3600)) return PhoneAction.Missing("El temporizador puede ser de hasta 24 horas.")
            return PhoneAction.Timer(seconds, "")
        }

        SEARCH.matchEntire(p)?.let { found ->
            return PhoneAction.WebSearch(o.substring(found.groups[1]!!.range.first).trim())
        }

        NAVIGATE.matchEntire(p)?.let { found ->
            return PhoneAction.Navigate(o.substring(found.groups[1]!!.range.first).trim())
        }

        OPEN.matchEntire(p)?.let { found ->
            val name = o.substring(found.groups[1]!!.range.first).trim()
            return if (name.isEmpty()) null else PhoneAction.OpenApp(name)
        }
        return null
    }

    private fun alarm(found: MatchResult, original: String): PhoneAction {
        val hour = if (found.groupValues[1] == "la una") 1 else found.groupValues[2].toIntOrNull() ?: 0
        val minute = when {
            found.groupValues[3].isNotEmpty() -> found.groupValues[3].toInt()
            found.groupValues[4].endsWith("media") -> 30
            found.groupValues[4].endsWith("cuarto") -> 15
            else -> 0
        }
        val suffix = found.groupValues[5].replace(".", "").trim()
        val period = found.groupValues[6]
        val afternoon = suffix == "pm" || period == "tarde" || period == "noche"
        val hour24 = when {
            afternoon && hour in 1..11 -> hour + 12
            (suffix == "am" || period == "manana") && hour == 12 -> 0
            else -> hour
        }
        if (hour24 !in 0..23 || minute !in 0..59) return PhoneAction.Missing("No entendí la hora. Prueba: pon una alarma a las 7:30.")
        val label = found.groups[7]?.let { original.substring(it.range.first).trim() }
            ?.removePrefix("para ")?.trim().orEmpty()
        return PhoneAction.Alarm(hour24, minute, label)
    }

    /** Elige la app que mejor coincide con lo que dijo el usuario. */
    sealed class AppChoice {
        data class Found(val app: InstalledApp) : AppChoice()
        data class Several(val apps: List<InstalledApp>) : AppChoice()
        object None : AppChoice()
    }

    fun pickApp(name: String, apps: List<InstalledApp>): AppChoice {
        val wanted = simplify(name)
        if (wanted.isEmpty()) return AppChoice.None
        val labeled = apps.map { simplify(it.label) to it }
        for (rule in listOf<(String) -> Boolean>(
            { it == wanted },
            { it.startsWith(wanted) },
            { it.contains(wanted) },
            { label -> wanted.split(" ").all { label.contains(it) } }
        )) {
            val matches = labeled.filter { rule(it.first) }.map { it.second }.distinctBy { it.packageName }
            when {
                matches.size == 1 -> return AppChoice.Found(matches[0])
                matches.size > 1 -> return AppChoice.Several(matches.sortedBy { it.label.length }.take(MAX_CHOICES))
            }
        }
        return AppChoice.None
    }

    private fun simplify(text: String) =
        ReminderParser.plain(text).replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()

    private fun digits(text: String) = text.filter { it.isDigit() || it == '+' }

    private const val MAX_CHOICES = 4

    private val POLITE = Regex("^[¿¡\\s]*(?:ari[,:]?\\s+)?(?:(?:por favor|porfa)[,]?\\s+)?(?:(?:puedes|podrias)\\s+)?")
    private val NUMBER = Regex("\\+?\\d[\\d\\s-]{6,}\\d")

    private val CAMERA = Regex("^(?:abre|abrir|abreme|enciende|activa)\\s+(?:la\\s+)?camara$|^(?:toma|tomame|saca)\\s+una\\s+foto$")
    private val SETTINGS = Regex(
        "^(?:abre|abrir|abreme)\\s+(?:los\\s+|la\\s+)?(?:ajustes|configuracion)(?:\\s+(?:de|del)(?:\\s+la)?\\s+(wifi|wi-fi|bluetooth|sonido|pantalla|bateria|ubicacion|notificaciones))?$"
    )
    private val WHATSAPP = Regex(
        "^(?:manda|mandale|envia|enviale|escribe|escribele)\\s+(?:un\\s+)?(?:mensaje\\s+(?:de|por|en)\\s+)?(?:whatsapp|whats|wasap|wats)\\b(?:\\s+(?:al|a|para)\\b)?"
    )
    private val SAYING = Regex("\\s*(?:que\\s+diga|diciendo|con\\s+el\\s+texto|:)\\s*")
    private val DIAL = Regex("^(?:llama|llamale|marca|marcale|marcame)(?:\\s+(?:a|al))?\\s+(.+)$")
    private val ALARM = Regex(
        "^(?:(?:pon|ponme|programa|programame|crea|activa)(?:\\s+(?:una|la))?\\s+alarma|despiertame)(?:\\s+para)?(?:\\s+manana)?\\s+" +
            "a\\s+(?:(la una)|las\\s+(\\d{1,2}))(?::(\\d{2}))?(\\s+y\\s+(?:media|cuarto))?" +
            "(\\s*(?:a\\.?m\\.?|p\\.?m\\.?)(?![a-z]))?(?:\\s+(?:de|en|por)\\s+la\\s+(manana|tarde|noche|madrugada))?" +
            "(?:\\s+(.+))?$"
    )
    private val TIMER = Regex(
        "^(?:pon|ponme|programa|inicia|activa)(?:\\s+(?:un|el))?\\s+(?:temporizador|timer|cronometro)(?:\\s+de)?\\s+(\\d{1,4}|un|una|media)\\s+(segundos?|minutos?|mins?|horas?)$"
    )
    private val SEARCH = Regex("^(?:(?:busca|buscame)(?:\\s+(?:en|por))?\\s+(?:google|internet|la\\s+web)|googlea)\\s+(.+)$")
    private val NAVIGATE = Regex(
        "^(?:llevame|como\\s+llego|guiame|navega|ruta)(?:\\s+(?:a|al|hasta|para|a\\s+la|hacia))?\\s+(.+)$"
    )
    private val OPEN = Regex("^(?:abre|abrir|abreme|ejecuta|inicia|lanza)\\s+(?:la\\s+app\\s+(?:de\\s+)?|la\\s+aplicacion\\s+(?:de\\s+)?)?(.+)$")
}
