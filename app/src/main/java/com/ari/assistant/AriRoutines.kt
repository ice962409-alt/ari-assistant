package com.ari.assistant

import android.content.SharedPreferences
import com.ari.assistant.agents.normalizeForAgents

/**
 * Rutinas: órdenes nuevas que le enseñas a Ari sin reinstalar nada.
 *
 * "Cuando te diga buenos días, dime qué tengo hoy y pon música" guarda la rutina
 * "buenos días" con 2 pasos. Después, al decir "buenos días", Ari hace cada paso
 * como si se lo dijeras tú, uno tras otro. Se guardan como texto (no como código)
 * y viajan en el respaldo. La lógica no usa Android, para poder probarla.
 */
data class Routine(val trigger: String, val steps: List<String>)

object AriRoutines {
    const val MAX_ROUTINES = 30
    const val MAX_STEPS = 6
    private const val MAX_TRIGGER_WORDS = 6
    const val PREFS_KEY = "rutinas"

    sealed class Request {
        data class Teach(val routine: Routine) : Request()
        object ListAll : Request()
        data class Forget(val trigger: String) : Request()
    }

    private val TEACH = Regex(
        "^(?:aprende (?:que )?|nueva rutina:? |rutina:? )?cuando (?:yo )?(?:te )?digas? (?:que )?[\"“']?(.+?)[\"”']?\\s*,\\s*(?:tu |entonces )?(.+)$"
    )
    private val TEACH_NO_COMMA = Regex(
        "^(?:aprende (?:que )?|nueva rutina:? |rutina:? )?cuando (?:yo )?(?:te )?digas? (?:que )?[\"“'](.+?)[\"”']\\s*(?:tu |entonces )?(.+)$"
    )
    private val LIST = Regex("^(?:cuales son |que )?(?:mis |tus )?rutinas(?: tienes| hay| me sabes| sabes)?$|^(?:que|cuales) rutinas (?:tienes|sabes|hay|te enseñe|te ensene)$")
    private val FORGET = Regex("^(?:olvida|borra|quita|elimina) (?:la )?rutina (?:de |del |de la )?[\"“']?(.+?)[\"”']?$")

    /** Verbos con los que empieza un paso; lo que no empiece así se pega al paso anterior. */
    private val STEP_STARTS = setOf(
        "dime", "di", "pon", "ponme", "abre", "abreme", "llama", "llamale", "manda", "mandale", "envia", "enviale",
        "dile", "busca", "buscame", "recuerdame", "apaga", "enciende", "prende", "muestra", "muestrame", "lee",
        "leeme", "cuentame", "toca", "reproduce", "haz", "preguntame", "que", "cual", "cuales", "como", "cuando",
        "cuanto", "cuantos", "donde", "quien", "activa", "desactiva", "quita", "avisame", "inicia", "empieza",
        "comienza", "mira", "revisa", "ensename", "explicame", "ayudame", "programa", "agenda", "anota", "apunta",
        "guarda", "escribe", "escribele", "despiertame", "sube", "baja", "silencia", "salta", "para", "deten",
        "traduce", "calcula", "ve", "vamos", "llevame", "navega", "toma", "saca", "graba", "hola", "buenos", "buenas"
    )

    /** Palabras que dices todo el tiempo: no pueden ser disparadores. */
    private val TOO_COMMON = setOf("si", "no", "ok", "okay", "vale", "va", "gracias", "ya", "listo", "bueno", "claro", "nada", "hola", "ari", "eso", "esto")

    /** Qué pediste: enseñar una rutina, ver las tuyas u olvidar una. null si no es de rutinas. */
    fun parse(text: String): Request? {
        val normalized = normalizeForAgents(text)
        if (LIST.matches(normalized)) return Request.ListAll
        FORGET.matchEntire(normalized)?.let { match ->
            return Request.Forget(cleanTrigger(match.groupValues[1]))
        }
        // El disparador se toma del texto con acentos para mostrarlo bonito; se compara normalizado.
        val lowered = text.trim().lowercase().replace(Regex("^ari[,:]?\\s+"), "")
        val match = TEACH_NO_COMMA.matchEntire(stripAccentsKeepCase(lowered)) ?: TEACH.matchEntire(stripAccentsKeepCase(lowered))
            ?: return null
        val trigger = cleanTrigger(match.groupValues[1])
        if (trigger.isEmpty() || trigger.split(" ").size > MAX_TRIGGER_WORDS) return null
        if (trigger in TOO_COMMON) return null
        val steps = splitSteps(match.groupValues[2])
        // El primer paso debe ser una orden ("dime…", "pon…", "abre…"), no una plática.
        if (steps.isEmpty() || steps[0].substringBefore(' ') !in STEP_STARTS) return null
        return Request.Teach(Routine(trigger, steps.take(MAX_STEPS)))
    }

    /** "dime qué tengo hoy y pon música, luego abre WhatsApp" → 3 pasos. */
    fun splitSteps(actions: String): List<String> {
        val pieces = actions
            .replace(Regex("\\s*[.;]\\s*$"), "")
            .split(Regex("\\s*,\\s*(?:y\\s+)?(?:luego|despues|entonces)?\\s*|\\s+y\\s+(?:luego\\s+|despues\\s+)?|\\s+(?:luego|despues)\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val steps = mutableListOf<String>()
        for (piece in pieces) {
            val first = piece.substringBefore(' ').trim('¿', '¡')
            if (steps.isEmpty() || first in STEP_STARTS) steps += piece else steps[steps.size - 1] = steps.last() + " y " + piece
        }
        return steps
    }

    /** La rutina que dispara este mensaje (el mensaje entero debe ser el disparador). */
    fun find(routines: List<Routine>, text: String): Routine? {
        val normalized = cleanTrigger(normalizeForAgents(text))
        if (normalized.isEmpty()) return null
        return routines.firstOrNull { it.trigger == normalized }
    }

    /** Agrega o reemplaza (mismo disparador). Si ya hay demasiadas, la más vieja se va. */
    fun add(routines: List<Routine>, routine: Routine): List<Routine> =
        (routines.filter { it.trigger != routine.trigger } + routine).takeLast(MAX_ROUTINES)

    fun remove(routines: List<Routine>, trigger: String): List<Routine> =
        routines.filter { it.trigger != cleanTrigger(normalizeForAgents(trigger)) }

    fun describe(routine: Routine): String =
        "\"${routine.trigger}\": " + routine.steps.mapIndexed { i, step -> "${i + 1}) $step" }.joinToString(" ")

    // Guardado: una rutina por línea, "disparador␟paso␟paso". Simple y sin librerías.
    private const val SEP = '\u001F'

    fun encode(routines: List<Routine>): String =
        routines.joinToString("\n") { r -> (listOf(r.trigger) + r.steps).joinToString(SEP.toString()) { it.replace('\n', ' ').replace(SEP, ' ') } }

    fun decode(saved: String?): List<Routine> =
        saved.orEmpty().lines().mapNotNull { line ->
            val parts = line.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) null else Routine(parts[0], parts.drop(1).take(MAX_STEPS))
        }.takeLast(MAX_ROUTINES)

    fun load(prefs: SharedPreferences) = decode(prefs.getString(PREFS_KEY, null))

    fun save(prefs: SharedPreferences, routines: List<Routine>) {
        prefs.edit().putString(PREFS_KEY, encode(routines)).apply()
    }

    private fun cleanTrigger(text: String) = text
        .lowercase()
        .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "") }
        .replace(Regex("[\"“”'¿?¡!.,;:]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun stripAccentsKeepCase(text: String) =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("\\s+"), " ").trim()
}
