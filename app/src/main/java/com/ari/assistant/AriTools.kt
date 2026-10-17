package com.ari.assistant

import com.ari.assistant.agents.PhoneAction
import com.ari.assistant.agents.PhoneCommands

/**
 * Tus apps son las herramientas de ARI (decisión del dueño, 14/10/2026).
 *
 * Gemma no toca el teléfono: cuando le pides algo que se hace con una app, escribe al
 * final de su respuesta un paso por línea, "HACER: <orden>", con las mismas frases que
 * ARI ya entiende ("cómo llego a casa", "pon Coldplay en Spotify"). ARI revisa cada paso:
 * - Lo seguro (abrir, buscar, mostrar, rutas, música, notas) lo hace sola.
 * - Lo delicado (mensajes, llamadas, correos, citas) te lo pregunta antes.
 * - Lo que no reconoce no lo hace: lo dice y queda como mejora.
 * Pagar, comprar o borrar no son herramientas: nunca los hace.
 * (Sin Android, para probarlo.)
 */
object AriTools {

    const val TAG = "HACER:"
    const val MAX_STEPS = 4

    enum class Risk { SAFE, ASK }

    /** Una familia de herramientas, con las frases que Gemma debe usar. */
    data class Family(val name: String, val examples: List<String>)

    val FAMILIES = listOf(
        Family(
            "Mensajes y llamadas (WhatsApp, SMS)",
            listOf(
                "manda un WhatsApp a <contacto> que diga <texto>",
                "mándale un mensaje a <contacto> que diga <texto>",
                "llama a <contacto>",
            )
        ),
        Family(
            "Mapas y transporte (Maps, Uber, DiDi)",
            listOf(
                "cómo llego a <lugar>",
                "pide un Uber a <lugar>",
                "pide un DiDi a <lugar>",
            )
        ),
        Family(
            "Música y videos (Spotify, YouTube)",
            listOf(
                "pon <canción o artista> en Spotify",
                "pon <video> en YouTube",
            )
        ),
        Family(
            "Productividad (calendario, correo, notas, búsquedas, apps)",
            listOf(
                "agenda <qué> el <día> a las <hora>",
                "manda un correo a <correo> que diga <texto>",
                "anota en Keep <texto>",
                "pon una alarma a las <hora>",
                "busca en Google <tema>",
                "abre <app>",
            )
        ),
    )

    /** La instrucción para Gemma (solo en la ARI del dueño). */
    fun instruction(): String = buildString {
        append("Tus herramientas son las apps del teléfono del usuario. Cuando te pida hacer algo con una app, ")
        append("contesta en una frase qué vas a hacer y al final escribe un paso por línea que empiece con \"$TAG\" ")
        append("seguido de la orden, usando exactamente una de estas formas:\n")
        FAMILIES.forEach { family ->
            append("- ").append(family.name).append(": ")
            append(family.examples.joinToString("; ") { "\"$it\"" }).append("\n")
        }
        append("Máximo $MAX_STEPS pasos. Usa los nombres de contactos tal como el usuario los dijo. ")
        append("Tú no envías ni llamas: ARI lo hace después, cuando el usuario dice que sí. Nunca digas \"ya lo mandé\" ")
        append("ni \"ya lo envié\"; di \"te lo preparo\" y escribe el paso. ")
        append("No escribas $TAG si el usuario solo platica o pregunta algo. ")
        append("Si algo no se puede con estas formas (por ejemplo pagar, comprar o borrar), no lo inventes: dilo y anótalo como MEJORA.")
    }

    /** Lo que se aclara cuando Gemma dice que ya hizo algo que no hizo. */
    const val FALSE_CLAIM_NOTE = "Ojo: no envié ni hice nada en el teléfono. Para mandarlo dime, por ejemplo: " +
        "manda un WhatsApp a Fernanda que diga hola."
    const val FALSE_CLAIM_SPOKEN = "Ojo: en realidad no lo envié. Dime: manda un WhatsApp a, y el nombre."

    private val CLAIM = Regex(
        "\\b(?:(?:ya|listo,?)\\s+(?:se\\s+)?(?:lo\\s+|la\\s+|le\\s+|les\\s+|te\\s+)?(?:mande|envie|llame|marque|escribi|agende|pague|compre)|" +
            "(?:he|hemos)\\s+(?:enviado|mandado|llamado|marcado)|(?:mensaje|whatsapp|correo)\\s+(?:enviado|mandado))\\b"
    )

    /** true si la respuesta dice que ya envió, llamó o pagó, pero no trae ningún paso "HACER:". */
    fun claimsDone(response: String): Boolean =
        stepsIn(response).isEmpty() && CLAIM.containsMatchIn(
            com.ari.assistant.agents.ReminderParser.plain(AriBrain.withoutNotes(response))
        )

    /** Los pasos que Gemma marcó, en orden. */
    fun stepsIn(response: String): List<String> =
        response.lines().map { it.trim().trim('*', '_', '-', ' ') }
            .filter { it.uppercase().startsWith(TAG) }
            .map { clean(clean(it.substringAfter(':')).trimEnd('.')) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_STEPS)

    private fun clean(text: String) = text.trim { it.isWhitespace() || it in "\"“”*`'" }

    /** Qué tan delicado es: lo que habla por ti o cambia tu agenda se pregunta. */
    fun risk(action: PhoneAction): Risk = when (action) {
        is PhoneAction.WhatsApp, is PhoneAction.WhatsAppContact,
        is PhoneAction.Sms, is PhoneAction.SmsContact,
        is PhoneAction.Email,
        is PhoneAction.Dial, is PhoneAction.DialContact,
        is PhoneAction.CalendarEvent -> Risk.ASK
        else -> Risk.SAFE
    }

    sealed class Step {
        abstract val command: String
        data class Run(override val command: String, val action: PhoneAction, val risk: Risk) : Step()
        data class Unknown(override val command: String) : Step()
    }

    /** Convierte los pasos de Gemma en acciones que ARI conoce. */
    fun plan(response: String): List<Step> = stepsIn(response).map { command ->
        when (val action = PhoneCommands.parse(command)) {
            null, is PhoneAction.Missing -> Step.Unknown(command)
            else -> Step.Run(command, action, risk(action))
        }
    }

    /** Cómo se lee el paso en la pregunta de confirmación. */
    fun describe(action: PhoneAction): String = when (action) {
        is PhoneAction.WhatsAppContact -> "WhatsApp a ${action.name}: \"${action.text}\""
        is PhoneAction.WhatsApp -> "WhatsApp al ${action.number}: \"${action.text}\""
        is PhoneAction.SmsContact -> "Mensaje a ${action.name}: \"${action.text}\""
        is PhoneAction.Sms -> "Mensaje al ${action.number}: \"${action.text}\""
        is PhoneAction.Email -> "Correo a ${action.to.ifEmpty { "(elegir)" }}: \"${action.text}\""
        is PhoneAction.DialContact -> "Llamar a ${action.name}"
        is PhoneAction.Dial -> "Llamar al ${action.number}"
        is PhoneAction.CalendarEvent -> "Agendar \"${action.title}\""
        else -> action.toString()
    }
}
