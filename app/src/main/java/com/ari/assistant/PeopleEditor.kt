package com.ari.assistant

import com.ari.assistant.agents.PeopleParser
import com.ari.assistant.agents.Person

/**
 * Lo que se escribe en la pantalla para editar a una persona de la libreta, convertido
 * en una ficha. Sin Android, para probarlo.
 */
object PeopleEditor {

    const val MAX_FACTS = 10
    private const val MAX_FACT_LENGTH = 200

    /** Lo que se puede escribir como cumpleaños: "5 de mayo", "5 mayo", "5/5" o "05-05" (día y mes). */
    fun parseBirthday(raw: String): String? {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return null
        Regex("^(\\d{1,2})\\s+(?:de\\s+)?(\\p{L}+)$").find(text)?.let {
            val value = PeopleParser.birthday(it.groupValues[1], it.groupValues[2]) ?: return null
            val (month, day) = value.split("-").map(String::toInt)
            return value.takeIf { day <= maxDay(month) }
        }
        Regex("^(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})$").find(text)?.let {
            val day = it.groupValues[1].toInt()
            val month = it.groupValues[2].toInt()
            if (month !in 1..12 || day !in 1..maxDay(month)) return null
            return "%02d-%02d".format(month, day)
        }
        return null
    }

    /** Un dato por línea, sin vacíos ni repetidos. */
    fun parseFacts(raw: String): List<String> =
        raw.lines().map { it.trim().trimStart('•', '-', ' ').trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_FACTS)
            .map { it.take(MAX_FACT_LENGTH) }

    /**
     * La ficha con los cambios, o un mensaje de lo que falta. [birthdayText] vacío borra
     * el cumpleaños; uno que no se entiende no se guarda (para no perder el anterior).
     */
    fun build(base: Person, name: String, relation: String, birthdayText: String, factsText: String): Result {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val cleanRelation = relation.trim().lowercase()
        if (cleanName.isEmpty() && cleanRelation.isEmpty()) return Result.Error("Escribe al menos el nombre o quién es (por ejemplo, primo).")
        val birthday = if (birthdayText.isBlank()) null else parseBirthday(birthdayText)
            ?: return Result.Error("No entendí el cumpleaños. Escríbelo como \"5 de mayo\".")
        return Result.Ok(
            base.copy(
                name = cleanName.ifEmpty { cleanRelation.replaceFirstChar { it.uppercaseChar() } }.replaceFirstChar { it.uppercaseChar() },
                relation = if (cleanRelation.isEmpty()) "" else PeopleParser.relation(cleanRelation),
                facts = parseFacts(factsText),
                birthday = birthday
            )
        )
    }

    /** Cómo se ve el cumpleaños guardado en el campo para editarlo. */
    fun birthdayText(person: Person): String = person.birthday?.let { PeopleParser.describeBirthday(it) }.orEmpty()

    sealed class Result {
        data class Ok(val person: Person) : Result()
        data class Error(val message: String) : Result()
    }

    private fun maxDay(month: Int) = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
