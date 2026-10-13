package com.ari.assistant.agents

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Entiende frases como "recuérdame mañana a las 9 llamar a mamá",
 * "recuérdame en 20 minutos sacar la ropa" o "avísame el viernes a las 5 de
 * la tarde que hay junta". Devuelve cuándo avisar y qué decir.
 */
object ReminderParser {

    sealed class Result {
        data class Ok(val dueAt: LocalDateTime, val text: String) : Result()
        data class Problem(val message: String) : Result()
    }

    /** true si el mensaje pide un recordatorio. [normalized] va sin acentos. */
    fun isRequest(normalized: String): Boolean = PREFIX.containsMatchIn(normalized)

    fun parse(message: String, now: LocalDateTime): Result {
        // Misma longitud que el original: así las posiciones sirven para recortar el texto real.
        val plain = plain(message)
        val prefix = PREFIX.find(plain) ?: return Result.Problem(HOW_TO)
        val removed = mutableListOf<IntRange>()
        removed.add(prefix.range)

        // Cada parte se busca solo donde no hay otra ya reconocida: así "de la mañana"
        // no se confunde con "mañana" (el día).
        fun take(regex: Regex): MatchResult? =
            regex.findAll(plain, prefix.range.last + 1)
                .firstOrNull { found -> removed.none { it.first <= found.range.last && found.range.first <= it.last } }
                ?.also { removed.add(it.range) }

        val relative = take(RELATIVE)
        val dueAt: LocalDateTime = if (relative != null) {
            val amount = when (val word = relative.groupValues[1]) {
                "un", "una" -> 1L
                "media" -> 0L
                else -> word.toLongOrNull() ?: return Result.Problem(HOW_TO)
            }
            val unit = relative.groupValues[2]
            when {
                relative.groupValues[1] == "media" -> now.plusMinutes(30)
                unit.startsWith("h") -> now.plusHours(amount)
                else -> now.plusMinutes(amount)
            }
        } else {
            val period = take(PERIOD)?.groupValues?.get(1)
            val day = take(DAY)
            val time = take(TIME)
            if (day == null && time == null) return Result.Problem(WHEN)

            val date = day?.let { dateFor(it.groupValues[1], now.toLocalDate()) }
            val explicitToday = day?.groupValues?.get(1) == "hoy"
            if (time == null) {
                LocalDateTime.of(date!!, DEFAULT_TIME)
                    .takeIf { it.isAfter(now) } ?: return Result.Problem(PAST)
            } else {
                val hour = if (time.groupValues[1] == "la una") 1 else time.groupValues[2].toInt()
                val minute = when {
                    time.groupValues[3].isNotEmpty() -> time.groupValues[3].toInt()
                    time.groupValues[5].endsWith("media") -> 30
                    time.groupValues[5].endsWith("cuarto") -> 15
                    else -> 0
                }
                val suffix = time.groupValues[4].replace(".", "")
                if (hour !in 0..23 || minute !in 0..59) return Result.Problem(HOW_TO)
                resolve(hour, minute, suffix, period, date, explicitToday, now)
                    ?: return Result.Problem(PAST)
            }
        }

        if (dueAt.isAfter(now.plusYears(1))) return Result.Problem("Solo puedo recordarte cosas dentro del próximo año.")

        val text = clean(message, removed)
        if (text.isEmpty()) return Result.Problem("¿Qué quieres que te recuerde? Por ejemplo: recuérdame mañana a las 9 llamar a mamá.")
        return Result.Ok(dueAt.withSecond(0).withNano(0), text)
    }

    private fun resolve(
        hour: Int, minute: Int, suffix: String, period: String?,
        date: LocalDate?, explicitToday: Boolean, now: LocalDateTime
    ): LocalDateTime? {
        val afternoon = suffix == "pm" || period == "tarde" || period == "noche"
        val morning = suffix == "am" || period == "manana" || period == "madrugada"
        val hour24 = when {
            afternoon && hour in 1..11 -> hour + 12
            morning && hour == 12 -> 0
            else -> hour
        }
        val known = afternoon || morning || hour > 12 || hour == 0

        if (date != null && !explicitToday) {
            // Otro día: "mañana a las 3" es en la tarde; "mañana a las 9", en la mañana.
            val h = if (!known && hour in 1..6) hour + 12 else hour24
            return LocalDateTime.of(date, LocalTime.of(h, minute))
        }

        val today = now.toLocalDate()
        val options = if (known) listOf(hour24) else listOf(hour24, hour24 + 12).filter { it < 24 }
        options.map { LocalDateTime.of(today, LocalTime.of(it, minute)) }
            .firstOrNull { it.isAfter(now) }
            ?.let { return it }
        // Ya pasó hoy: si dijo "hoy" es un error; si no, es mañana.
        if (explicitToday) return null
        return LocalDateTime.of(today.plusDays(1), LocalTime.of(hour24, minute))
    }

    private fun dateFor(word: String, today: LocalDate): LocalDate = when (word) {
        "hoy" -> today
        "manana" -> today.plusDays(1)
        "pasado manana" -> today.plusDays(2)
        else -> {
            val target = WEEKDAYS.getValue(word.removePrefix("el ").trim())
            var date = today.plusDays(1)
            while (date.dayOfWeek != target) date = date.plusDays(1)
            date
        }
    }

    /** Quita del mensaje original las partes que eran la orden y la fecha. */
    private fun clean(message: String, removed: List<IntRange>): String {
        val keep = StringBuilder()
        message.forEachIndexed { index, char ->
            keep.append(if (removed.any { index in it }) ' ' else char)
        }
        return keep.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^(?i)(?:que|de|a)\\s+"), "")
            .trim(' ', ',', '.', '!', '?', '¡', '¿', ':', ';')
            .trim()
    }

    /** Minúsculas y sin acentos, carácter por carácter, sin cambiar la longitud. */
    fun plain(text: String): String = buildString {
        text.forEach { char ->
            val base = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            append((base.firstOrNull() ?: char).lowercaseChar())
        }
    }

    private val WEEKDAYS = mapOf(
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY, "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )
    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    private val PREFIX = Regex("^[¿¡\\s]*(?:ari[,:]?\\s+)?(?:recuerdame|recordame|avisame)\\b[,:]?")
    private val RELATIVE = Regex("\\ben\\s+(\\d{1,4}|un|una|media)\\s+(minutos?|mins?|horas?|hrs?|hora)\\b")
    private val PERIOD = Regex("\\b(?:de|en|por)\\s+la\\s+(manana|tarde|noche|madrugada)\\b")
    private val DAY = Regex("\\b(hoy|pasado manana|manana|el\\s+(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo))\\b")
    private val TIME = Regex(
        "\\ba\\s+(?:(la una)|las\\s+(\\d{1,2}))(?::(\\d{2}))?(?:\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z]))?(\\s+y\\s+(?:media|cuarto))?"
    )

    private const val HOW_TO =
        "No entendí el recordatorio. Prueba así: recuérdame mañana a las 9 llamar a mamá, o recuérdame en 20 minutos sacar la ropa."
    private const val WHEN =
        "¿Para cuándo? Dime, por ejemplo: recuérdame mañana a las 9 llamar a mamá, o en 30 minutos."
    private const val PAST = "Esa hora ya pasó. Dime una hora que todavía no llegue."
}
