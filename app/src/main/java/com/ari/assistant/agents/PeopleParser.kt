package com.ari.assistant.agents

/** Una persona que el usuario le presentó a Ari. [birthday] es "MM-DD". */
data class Person(
    val id: Long,
    val name: String,
    val relation: String = "",
    val facts: List<String> = emptyList(),
    val birthday: String? = null
)

/**
 * Entiende frases sobre las personas de tu vida, sin Android (para probarlo):
 * "mi primo Luis es dentista", "mi mamá se llama Rosa", "Ana es mi jefa",
 * "Luis cumple el 5 de mayo", "qué sabes de Luis", "olvida a Luis",
 * "a quién conoces", "quién cumple años".
 *
 * Solo toma frases cortas y que afirman algo. Si le preguntas o le pides
 * consejo ("mi jefe es pesado, ¿qué hago?"), la frase es para Gemma.
 */
object PeopleParser {

    sealed class Intent {
        data class Remember(
            val name: String?,
            val relation: String?,
            val fact: String?,
            val birthday: String?
        ) : Intent()
        data class Ask(val name: String) : Intent()
        data class Forget(val name: String) : Intent()
        object ListAll : Intent()
        object Birthdays : Intent()
    }

    private val RELATIONS = listOf(
        "mejor amiga", "mejor amigo", "compañera de trabajo", "compañero de trabajo",
        "mamá", "mama", "papá", "papa", "madre", "padre",
        "hermana", "hermano", "prima", "primo", "tía", "tia", "tío", "tio",
        "abuela", "abuelo", "hija", "hijo", "sobrina", "sobrino", "nieta", "nieto",
        "novia", "novio", "esposa", "esposo", "pareja", "amiga", "amigo",
        "jefa", "jefe", "compañera", "compañero", "vecina", "vecino",
        "suegra", "suegro", "cuñada", "cuñado", "doctora", "doctor", "maestra", "maestro"
    ).sortedByDescending { it.length }

    /** Relaciones que suele haber solo una: "mi mamá se llama Rosa" completa a "mi mamá". */
    private val SINGLE = setOf("mamá", "papá", "madre", "padre", "novia", "novio", "esposa", "esposo", "pareja", "jefa", "jefe")

    private const val REL = "(mejor amiga|mejor amigo|compañera de trabajo|compañero de trabajo|mamá|mama|papá|papa|madre|padre|hermana|hermano|prima|primo|tía|tia|tío|tio|abuela|abuelo|hija|hijo|sobrina|sobrino|nieta|nieto|novia|novio|esposa|esposo|pareja|amiga|amigo|jefa|jefe|compañera|compañero|vecina|vecino|suegra|suegro|cuñada|cuñado|doctora|doctor|maestra|maestro)"
    private const val NAME = "([\\p{L}]{2,}(?:\\s+[\\p{Lu}][\\p{L}]+)?)"
    private const val VERB = "(es|está|esta|tiene|trabaja|vive|estudia|juega|cumple|le gusta|le encanta|odia|no le gusta|se dedica)"
    private const val PREFIX = "^(?:ari[,:]?\\s+)?(?:recuerda(?:\\s+que)?\\s+|anota(?:\\s+que)?\\s+|guarda\\s+que\\s+)?"

    private val OPTIONS = setOf(RegexOption.IGNORE_CASE)

    // "mi primo Luis es dentista", "mi mamá trabaja en un hospital"
    private val MY_REL_NAME_VERB = Regex("${PREFIX}mi\\s+$REL(?:\\s+$NAME)?\\s+$VERB\\b\\s*(.*)$", OPTIONS)
    // "mi mamá se llama Rosa (y es doctora)"
    private val MY_REL_IS_CALLED = Regex("${PREFIX}mi\\s+$REL\\s+se\\s+llama\\s+$NAME(?:\\s*[,y]+\\s*(.*))?$", OPTIONS)
    // "Ana es mi jefa (y le gusta el café)"
    private val NAME_IS_MY_REL = Regex("${PREFIX}$NAME\\s+es\\s+mi\\s+$REL(?:\\s*[,y]+\\s*(.*))?$", OPTIONS)
    // "te presento a Luis, (es) mi primo"
    private val INTRODUCE = Regex("^(?:ari[,:]?\\s+)?(?:te presento a|conoce a)\\s+$NAME(?:\\s*,?\\s*(?:es\\s+)?mi\\s+$REL)?(?:\\s*[,.]\\s*(.*))?$", OPTIONS)
    // "Luis cumple (años) el 5 de mayo", "el cumpleaños de Luis es el 5 de mayo"
    private val NAME_BIRTHDAY = Regex("${PREFIX}(?:el\\s+cumpleaños\\s+de\\s+(?:mi\\s+$REL\\s+)?$NAME\\s+es|$NAME\\s+cumple(?:\\s+años)?)\\s+(?:el\\s+)?(\\d{1,2})\\s+de\\s+(\\p{L}+)$", OPTIONS)

    private val ASK = Regex("^(?:que sabes de|quien es|hablame de|cuentame de|que recuerdas de)\\s+(?:mi\\s+)?(.+)$")
    private val FORGET = Regex("^olvida a\\s+(?:mi\\s+)?(.+)$")
    private val LIST = Regex("^(?:a quien(?:es)? conoces|que personas conoces|mis personas|a quien te he presentado)$")
    private val BIRTHDAYS = Regex("^(?:quien cumple(?: anos)?(?: pronto)?|(?:proximos )?cumpleanos(?: proximos)?|que cumpleanos vienen)$")

    // Si hay pregunta o piden ayuda, la frase es para Gemma aunque mencione a alguien.
    private val ASKS_FOR_HELP = Regex("\\?|\\b(?:que hago|que hacer|que le|como le|ayudame|consejo|no se|pero|porque|por que)\\b")
    private const val MAX_WORDS = 18

    private val PRONOUNS = setOf("el", "ella", "eso", "esto", "que", "quien", "alguien", "nadie", "todo", "mi", "tu", "su")
    private val MONTHS = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    fun parse(message: String, normalized: String = normalizeForAgents(message)): Intent? {
        if (LIST.matches(normalized)) return Intent.ListAll
        if (BIRTHDAYS.matches(normalized)) return Intent.Birthdays
        FORGET.matchEntire(normalized)?.let { return Intent.Forget(it.groupValues[1].trim()) }
        ASK.matchEntire(normalized)?.let { return Intent.Ask(it.groupValues[1].trim()) }

        val text = message.trim().trimEnd('.', '!', ' ')
        if (ASKS_FOR_HELP.containsMatchIn(normalized) || text.split(Regex("\\s+")).size > MAX_WORDS) return null

        NAME_BIRTHDAY.matchEntire(text)?.let { found ->
            val relation = found.groupValues[1].ifEmpty { null }
            val name = found.groupValues[2].ifEmpty { found.groupValues[3] }
            val birthday = birthday(found.groupValues[4], found.groupValues[5]) ?: return null
            if (isPronoun(name)) return null
            return Intent.Remember(cap(name), relation?.let(::relation), null, birthday)
        }
        MY_REL_IS_CALLED.matchEntire(text)?.let { found ->
            val name = found.groupValues[2]
            if (isPronoun(name)) return null
            return remember(cap(name), relation(found.groupValues[1]), found.groupValues[3])
        }
        INTRODUCE.matchEntire(text)?.let { found ->
            val name = found.groupValues[1]
            if (isPronoun(name)) return null
            return remember(cap(name), found.groupValues[2].ifEmpty { null }?.let(::relation), found.groupValues[3])
        }
        NAME_IS_MY_REL.matchEntire(text)?.let { found ->
            val name = found.groupValues[1]
            // "Ana es mi jefa" sí; "Hoy es mi primo…" no: el nombre va con mayúscula.
            if (isPronoun(name) || !name.first().isUpperCase()) return null
            return remember(name, relation(found.groupValues[2]), found.groupValues[3])
        }
        MY_REL_NAME_VERB.matchEntire(text)?.let { found ->
            val relation = relation(found.groupValues[1])
            val name = found.groupValues[2].ifEmpty { null }?.takeUnless(::isPronoun)?.let(::cap)
            val fact = (found.groupValues[3] + " " + found.groupValues[4]).trim()
            if (found.groupValues[4].isBlank()) return null
            return remember(name, relation, fact)
        }
        return null
    }

    private fun remember(name: String?, relation: String?, rest: String?): Intent.Remember {
        val fact = rest?.trim()?.trimEnd('.', '!', ',')?.takeIf { it.isNotEmpty() }
        val birthday = fact?.let { Regex("cumple(?:\\s+años)?\\s+(?:el\\s+)?(\\d{1,2})\\s+de\\s+(\\p{L}+)", OPTIONS).find(it) }
            ?.let { birthday(it.groupValues[1], it.groupValues[2]) }
        return Intent.Remember(name, relation, fact?.takeIf { birthday == null || !it.startsWith("cumple", ignoreCase = true) }, birthday)
    }

    /** "5", "mayo" → "05-05". */
    fun birthday(day: String, month: String): String? {
        val d = day.toIntOrNull() ?: return null
        val m = MONTHS.indexOf(normalizeForAgents(month)) + 1
        if (m == 0 || d !in 1..31) return null
        return "%02d-%02d".format(m, d)
    }

    /** "05-05" → "5 de mayo". */
    fun describeBirthday(value: String): String {
        val (m, d) = value.split("-").map { it.toInt() }
        return "$d de ${MONTHS[m - 1]}"
    }

    /** Escribe la relación con acentos ("mama" → "mamá"). */
    fun relation(raw: String): String {
        val lower = raw.lowercase()
        return when (lower) {
            "mama" -> "mamá"
            "papa" -> "papá"
            "tia" -> "tía"
            "tio" -> "tío"
            else -> lower
        }
    }

    fun isSingle(relation: String) = relation in SINGLE

    private fun isPronoun(name: String) = normalizeForAgents(name).split(" ").first() in PRONOUNS ||
        RELATIONS.any { normalizeForAgents(it) == normalizeForAgents(name) }

    private fun cap(name: String) = name.split(Regex("\\s+")).joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }
}
