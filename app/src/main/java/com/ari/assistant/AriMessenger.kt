package com.ari.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ari.assistant.agents.Agent
import com.ari.assistant.agents.AgentResult
import com.ari.assistant.agents.AgentTask
import com.ari.assistant.agents.PhoneAction
import com.ari.assistant.agents.PhoneCommands
import com.ari.assistant.agents.PhoneLauncher

/**
 * Agente de recados entre Ari: "dile a mi mamá que llego a las 8". Busca a la persona en
 * tus contactos y abre tu app de mensajes con el recado ya escrito ("[Ari de Javier] …");
 * tú tocas enviar. Solo cuando habla el dueño (nunca un invitado).
 *
 * Ari no pide permisos de SMS: Android y algunos teléfonos (Xiaomi/POCO) no dejan
 * instalar apps fuera de la tienda que los piden, por seguridad contra fraudes.
 */
class AriMessageAgent(
    private val phone: PhoneLauncher,
    private val ownerName: () -> String,
    /** Los contactos de recados guardados (acceso rápido). */
    private val quick: () -> List<QuickContact> = { emptyList() },
    /** Máximo de contactos de recados; null sin límite (Ari CEO). */
    private val limit: Int? = null,
    /** true si Ari CEO toca "enviar" sola (Accesibilidad). */
    private val autoSend: () -> Boolean = { false }
) : Agent {

    /** Recado que espera a que digas si guardas al contacto (la app pregunta y lo vuelve a intentar). */
    @Volatile
    var pendingSave: QuickContact? = null

    /** true cuando falta el permiso de contactos: la app lo pide y lo vuelve a intentar. */
    @Volatile
    var needsPermission = false

    override val id = "mensajes"
    override val name = "Recados entre Ari"
    override val description = "Prepara un recado por SMS; si la otra persona tiene Ari, su Ari se lo dice."
    override val examples = listOf("dile a mi mamá que llego a las 8", "avísale a Laura que ya salí")

    override fun match(message: String, normalized: String): AgentTask? =
        AriMessages.request(message)?.let { AgentTask(message, ACTION, message) }

    override fun execute(task: AgentTask): AgentResult {
        val request = AriMessages.request(task.argument)
            ?: return AgentResult("No entendí el recado. Prueba: dile a mi mamá que llego a las 8.", success = false)
        val contacts = phone.contacts() ?: run {
            needsPermission = true
            return AgentResult(
                "Para saber quién es ${request.recipient} necesito ver tus contactos. Si aceptas, preparo el recado enseguida.",
                success = false
            )
        }
        val contact = when (val choice = PhoneCommands.pickContact(request.recipient, contacts)) {
            is PhoneCommands.ContactChoice.Found -> choice.contact
            is PhoneCommands.ContactChoice.Several -> return AgentResult(
                "Tienes varios contactos así: ${choice.contacts.joinToString(", ") { it.name }}. ¿A cuál? Dime el nombre completo.",
                success = false
            )
            PhoneCommands.ContactChoice.None ->
                return AgentResult("No encontré a \"${request.recipient}\" en tus contactos.", success = false)
        }
        when (val check = QuickContactRules.check(contact.number, quick(), limit)) {
            is QuickContactRules.Check.Saved -> Unit
            is QuickContactRules.Check.AskToSave -> {
                pendingSave = QuickContact(contact.name, contact.number)
                val left = check.left?.let { " (te quedan $it de ${QuickContactRules.LIMIT_OTHERS})" } ?: ""
                return AgentResult("¿Guardo a ${contact.name} en tus contactos de recados$left? Así lo tienes a mano en la burbuja.", success = false)
            }
            is QuickContactRules.Check.Full -> return AgentResult(
                "Ya tienes ${check.saved.size} contactos de recados: ${check.saved.joinToString(", ") { it.name }}. " +
                    "Es el máximo. Para mandarle a ${contact.name}, quita uno en Ajustes → Recados entre Ari.",
                success = false
            )
        }
        return send(contact.name, contact.number, request.text)
    }

    /** Abre tu app de mensajes con el recado listo (tú tocas enviar). */
    fun send(name: String, number: String, text: String): AgentResult {
        val sms = AriMessages.encode(ownerName(), text)
        val auto = autoSend()
        if (auto) AutoSend.arm(sms, name)
        return if (phone.launch(PhoneAction.Sms(number, sms))) {
            if (auto) AgentResult("Mandando el recado a $name: \"$text\".")
            else AgentResult("Abrí tu app de mensajes con el recado para $name: \"$text\". Toca enviar. Si tiene Ari, ella se lo dice.")
        } else {
            AgentResult("No encontré una app de mensajes para mandar el recado.", success = false)
        }
    }

    private companion object {
        const val ACTION = "recado"
    }
}

/**
 * Recados que llegan de otras Ari. Como Ari no lee tus SMS, la persona le comparte el
 * mensaje (Compartir → Ari) o lo pega en el chat, y Ari lo reconoce por la marca "[Ari de …]".
 */
object AriMessenger {

    /** Quien muestra los recados mientras Ari está abierta (MainActivity). */
    interface Listener {
        /** true si el recado se vio en la pantalla de Ari. */
        fun onAriMessage(text: String): Boolean
    }

    @Volatile
    var listener: Listener? = null

    private const val PREFS = "ari_recados"
    private const val KEY_INBOX = "pendientes"

    /** Guarda el recado para mostrarlo en el chat la próxima vez que abras Ari. */
    fun keep(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = prefs.getStringSet(KEY_INBOX, emptySet()).orEmpty().toMutableSet()
        list.add("${System.currentTimeMillis()}|$text")
        prefs.edit().putStringSet(KEY_INBOX, list.toSortedSet().toList().takeLast(20).toSet()).apply()
    }

    /** Los recados guardados, en orden; se borran al leerlos. */
    fun takeKept(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = prefs.getStringSet(KEY_INBOX, emptySet()).orEmpty()
        prefs.edit().remove(KEY_INBOX).apply()
        return list.sortedBy { it.substringBefore('|').toLongOrNull() ?: 0L }.map { it.substringAfter('|') }
    }

    internal fun deliver(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            val target = listener
            if (target == null || !target.onAriMessage(text)) {
                if (target == null) keep(context, text)
                AriChat.ariSays(context, text)
            }
        }
    }
}
