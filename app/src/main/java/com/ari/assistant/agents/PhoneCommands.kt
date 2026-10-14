package com.ari.assistant.agents

import java.time.LocalDateTime

/** Una acción en el teléfono que Ari puede hacer por ti. */
sealed class PhoneAction {
    data class OpenApp(val name: String) : PhoneAction()
    data class Dial(val number: String) : PhoneAction()
    /** "llama a mamá": se busca el número en los contactos. */
    data class DialContact(val name: String) : PhoneAction()
    /** [number] vacío: WhatsApp pregunta a quién enviarlo. */
    data class WhatsApp(val number: String, val text: String) : PhoneAction()
    data class WhatsAppContact(val name: String, val text: String) : PhoneAction()
    /** Mensaje de texto (SMS) listo para enviar; [number] vacío: el usuario elige a quién. */
    data class Sms(val number: String, val text: String) : PhoneAction()
    data class SmsContact(val name: String, val text: String) : PhoneAction()
    data class Email(val to: String, val text: String) : PhoneAction()
    /** [app]: "spotify", "youtube music", "deezer" o vacío para la app de música del teléfono. */
    data class PlayMusic(val query: String, val app: String) : PhoneAction()
    data class YouTube(val query: String) : PhoneAction()
    data class CalendarEvent(val title: String, val start: LocalDateTime) : PhoneAction()
    data class Alarm(val hour: Int, val minute: Int, val label: String) : PhoneAction()
    data class Timer(val seconds: Int, val label: String) : PhoneAction()
    data class WebSearch(val query: String) : PhoneAction()
    data class Navigate(val place: String) : PhoneAction()
    /** Pedir un viaje: abre Uber o DiDi con el destino listo; el viaje lo confirmas tú. */
    data class Ride(val place: String, val app: String) : PhoneAction()
    /** Una nota en Keep (o tu app de notas): se abre con el texto listo para guardar. */
    data class Note(val text: String) : PhoneAction()
    object Camera : PhoneAction()
    data class Settings(val section: String) : PhoneAction()
    /** La frase pedía una acción, pero falta algo; [message] explica qué. */
    data class Missing(val message: String) : PhoneAction()
}

data class InstalledApp(val label: String, val packageName: String)

data class PhoneContact(val name: String, val number: String)

/**
 * Entiende órdenes como "abre WhatsApp", "llama al 55 1234 5678",
 * "manda un WhatsApp al 5512345678 que diga ya voy", "pon una alarma a las 7",
 * "pon un temporizador de 10 minutos", "busca en Google recetas de pozole",
 * "cómo llego al aeropuerto". Sin Android, para poder probarlo.
 */
object PhoneCommands {

    fun parse(message: String, now: LocalDateTime = LocalDateTime.now()): PhoneAction? {
        val original = message.trim()
        // Misma longitud que el original, para recortar el texto real (con acentos y mayúsculas).
        val plain = ReminderParser.plain(original)
        val start = POLITE.find(plain)?.range?.last?.plus(1) ?: 0
        val p = plain.substring(start).trimEnd('.', '!', '?', ' ')
        val o = original.substring(start).trimEnd('.', '!', '?', ' ')

        CAMERA.matchEntire(p)?.let { return PhoneAction.Camera }

        RIDE.matchEntire(p)?.let { found ->
            val app = if (found.groupValues[1] == "didi") "didi" else "uber"
            val place = found.groups[2]?.let { o.substring(it.range.first).trim() }.orEmpty()
            return PhoneAction.Ride(place, app)
        }

        NOTE.matchEntire(p)?.let { found ->
            val text = o.substring(found.groups[1]!!.range.first).trim()
            return if (text.isEmpty()) null else PhoneAction.Note(text)
        }

        SETTINGS.matchEntire(p)?.let { found ->
            return PhoneAction.Settings(found.groupValues[1].ifEmpty { "general" })
        }

        WHATSAPP.find(p)?.let { found ->
            if (found.range.first != 0) return@let
            return message(o, p, found.range.last + 1, ::whatsAppAction,
                "¿Qué le escribo? Dime, por ejemplo: manda un WhatsApp a Juan que diga ya voy.") ?: return null
        }

        SMS.find(p)?.let { found ->
            if (found.range.first != 0) return@let
            return message(o, p, found.range.last + 1, ::smsAction,
                "¿Qué le escribo? Dime, por ejemplo: mándale un mensaje a mamá que diga ya llegué.") ?: return null
        }

        EMAIL.find(p)?.let { found ->
            if (found.range.first != 0) return@let
            val rest = o.substring(found.range.last + 1)
            val restPlain = p.substring(found.range.last + 1)
            val address = ADDRESS.find(rest)
            var textStart = address?.range?.last?.plus(1) ?: 0
            SAYING_EMAIL.find(restPlain, textStart)?.let { textStart = it.range.last + 1 }
            val text = rest.substring(textStart).trim().trim(':', ',', ' ')
            // "escribe un correo para mi jefe pidiendo vacaciones" pide redactarlo: eso lo hace Gemma.
            if (address == null && textStart == 0) return null
            return PhoneAction.Email(address?.value.orEmpty(), text)
        }

        DIAL.matchEntire(p)?.let { found ->
            val target = found.groupValues[1].trim()
            val number = NUMBER.matchEntire(target)
            // "llama la atención que…" es conversación, no una llamada.
            if (number == null && target.split(" ").size > MAX_NAME_WORDS) return null
            return if (number != null) PhoneAction.Dial(digits(number.value))
            else PhoneAction.DialContact(o.substring(found.groups[1]!!.range.first).trim())
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

        CALENDAR.find(p)?.let { found ->
            if (found.range.first != 0) return@let
            return when (val parsed = ReminderParser.parse(o, now, CALENDAR)) {
                is ReminderParser.Result.Ok ->
                    PhoneAction.CalendarEvent(parsed.text.replace(Regex("^(?i)(?:una?\\s+)?(?:evento\\s+)?"), "").ifEmpty { parsed.text }, parsed.dueAt)
                is ReminderParser.Result.Problem ->
                    PhoneAction.Missing("¿Qué y cuándo lo agendo? Dime, por ejemplo: agenda cita con el dentista el viernes a las 5.")
            }
        }

        YOUTUBE.matchEntire(p)?.let { found ->
            return PhoneAction.YouTube(o.substring(found.groups[1]!!.range.first).trim())
        }

        PLAY_ANY.matchEntire(p)?.takeIf { MUSIC_IN_APP.matchEntire(p) == null }?.let { found ->
            return PhoneAction.PlayMusic(o.substring(found.groups[1]!!.range.first).trim(), "")
        }

        (MUSIC.matchEntire(p) ?: MUSIC_IN_APP.matchEntire(p))?.let { found ->
            val query = o.substring(found.groups[1]!!.range.first, found.groups[1]!!.range.last + 1).trim()
            val app = found.groups[2]?.value?.replace(Regex("\\s+"), " ").orEmpty()
            return if (app == "youtube") PhoneAction.YouTube(query) else PhoneAction.PlayMusic(query, app)
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

    /**
     * WhatsApp y SMS: número o nombre del contacto, y el texto después de "que diga".
     * Sin número ni "que diga" devuelve null: "escribe un mensaje bonito para mi novia"
     * pide que Ari lo redacte, no que lo envíe.
     */
    private fun message(
        o: String, p: String, after: Int,
        build: (number: String, name: String, text: String) -> PhoneAction,
        missing: String
    ): PhoneAction? {
        val rest = o.substring(after)
        val restPlain = p.substring(after)
        val number = NUMBER.find(restPlain)
        var textStart = number?.range?.last?.plus(1) ?: 0
        val saying = SAYING.find(restPlain, textStart)
        saying?.let { textStart = it.range.last + 1 }
        val text = rest.substring(textStart).trim().trim(':', ',', ' ')
        if (text.isEmpty()) return PhoneAction.Missing(missing)
        if (number == null && saying == null) return null
        if (number == null && saying != null) {
            val name = rest.substring(0, saying.range.first).trim().trim(',', ' ')
            if (name.isNotEmpty() && name.split(" ").size <= MAX_NAME_WORDS) return build("", name, text)
        }
        return build(number?.value?.let(::digits).orEmpty(), "", text)
    }

    private fun whatsAppAction(number: String, name: String, text: String): PhoneAction =
        if (name.isNotEmpty()) PhoneAction.WhatsAppContact(name, text) else PhoneAction.WhatsApp(number, text)

    private fun smsAction(number: String, name: String, text: String): PhoneAction =
        if (name.isNotEmpty()) PhoneAction.SmsContact(name, text) else PhoneAction.Sms(number, text)

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
        val matches = best(name, apps, { it.label }, { it.packageName })
        return when {
            matches.isEmpty() -> AppChoice.None
            matches.size == 1 -> AppChoice.Found(matches[0])
            else -> AppChoice.Several(matches.sortedBy { it.label.length }.take(MAX_CHOICES))
        }
    }

    sealed class ContactChoice {
        data class Found(val contact: PhoneContact) : ContactChoice()
        data class Several(val contacts: List<PhoneContact>) : ContactChoice()
        object None : ContactChoice()
    }

    /** Elige el contacto por su nombre, sin importar acentos ni mayúsculas ("mama" encuentra "Mamá"). */
    fun pickContact(name: String, contacts: List<PhoneContact>): ContactChoice {
        // Un contacto con varios números aparece una vez (gana el primero: el principal).
        val matches = best(name, contacts, { it.name }, { simplify(it.name) })
        return when {
            matches.isEmpty() -> ContactChoice.None
            matches.size == 1 -> ContactChoice.Found(matches[0])
            else -> ContactChoice.Several(matches.sortedBy { it.name.length }.take(MAX_CHOICES))
        }
    }

    /** Lo más parecido a [name]: primero igual, luego empieza con, luego contiene, luego todas las palabras. */
    private fun <T> best(name: String, items: List<T>, label: (T) -> String, key: (T) -> String): List<T> {
        val wanted = simplify(name)
        if (wanted.isEmpty()) return emptyList()
        val labeled = items.map { simplify(label(it)) to it }
        for (rule in listOf<(String) -> Boolean>(
            { it == wanted },
            { it.startsWith(wanted) },
            { it.contains(wanted) },
            { text -> wanted.split(" ").all { text.contains(it) } }
        )) {
            val matches = labeled.filter { rule(it.first) }.map { it.second }.distinctBy(key)
            if (matches.isNotEmpty()) return matches
        }
        return emptyList()
    }

    private fun simplify(text: String) =
        ReminderParser.plain(text).replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()

    private fun digits(text: String) = text.filter { it.isDigit() || it == '+' }

    private const val MAX_CHOICES = 4
    private const val MAX_NAME_WORDS = 3

    private val POLITE = Regex("^[¿¡\\s]*(?:ari[,:]?\\s+)?(?:(?:por favor|porfa)[,]?\\s+)?(?:(?:puedes|podrias)\\s+)?")
    private val NUMBER = Regex("\\+?\\d[\\d\\s-]{6,}\\d")

    private val RIDE = Regex("^(?:pide|pideme|pedir|llama|llamame|consigueme|consigue)\\s+un\\s+(uber|didi|taxi)(?:\\s+(?:a|al|para|hacia|hasta|que\\s+me\\s+lleve\\s+a)\\s+(.+))?$")
    private val NOTE = Regex("^(?:anota|apunta|guarda|escribe)\\s+en\\s+(?:google\\s+)?(?:keep|notas|mis\\s+notas)[:,]?\\s+(?:que\\s+)?(.+)$")
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
    // Va después de WHATSAPP: "manda un mensaje por WhatsApp" ya se atendió.
    private val SMS = Regex(
        "^(?:manda|mandale|envia|enviale|escribe|escribele)\\s+(?:un\\s+)?(?:mensaje(?:\\s+de\\s+texto)?|sms)\\b(?:\\s+(?:al|a|para)\\b)?"
    )
    private val EMAIL = Regex(
        "^(?:manda|mandale|envia|enviale|escribe|escribele)\\s+(?:un\\s+)?(?:correo(?:\\s+electronico)?|email|e-mail|mail)\\b(?:\\s+(?:al|a|para)\\b)?"
    )
    private val ADDRESS = Regex("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")
    private val SAYING_EMAIL = Regex("\\s*(?:que\\s+diga|diciendo|sobre|con\\s+el\\s+texto|:)\\s*")
    private val CALENDAR = Regex(
        "^(?:agenda|agendame|agendar|(?:anota|agrega|pon|apunta)(?:me)?\\s+en\\s+(?:el|mi)\\s+calendario|programa\\s+(?:una\\s+)?(?:cita|reunion|junta))\\b[,:]?"
    )
    private val YOUTUBE = Regex("^(?:busca|buscame|pon|ponme)(?:\\s+(?:en|por))?\\s+youtube\\s+(.+)$")
    private val MUSIC = Regex(
        "^(?:pon|ponme|reproduce|toca)\\s+(?:musica|canciones|la\\s+cancion|el\\s+album|algo)(?:\\s+de)?\\s+(.+?)(?:\\s+en\\s+(spotify|youtube\\s+music|youtube|deezer))?$"
    )
    private val MUSIC_IN_APP = Regex("^(?:pon|ponme|reproduce)\\s+(.+?)\\s+en\\s+(spotify|youtube\\s+music|youtube|deezer)$")
    private val PLAY_ANY = Regex("^reproduce\\s+(.+)$")
    private val SEARCH = Regex("^(?:(?:busca|buscame)(?:\\s+(?:en|por))?\\s+(?:google|internet|la\\s+web)|googlea)\\s+(.+)$")
    private val NAVIGATE = Regex(
        "^(?:llevame|como\\s+llego|guiame|navega|ruta)(?:\\s+(?:a|al|hasta|para|a\\s+la|hacia))?\\s+(.+)$"
    )
    private val OPEN = Regex("^(?:abre|abrir|abreme|ejecuta|inicia|lanza|accede\\s+a|entra\\s+a|entra\\s+en|metete\\s+a|metete\\s+en|ingresa\\s+a)\\s+(?:la\\s+app\\s+(?:de\\s+)?|la\\s+aplicacion\\s+(?:de\\s+)?)?(.+)$")
}
