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
    private val ownerName: () -> String
) : Agent {

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
        val sms = AriMessages.encode(ownerName(), request.text)
        return if (phone.launch(PhoneAction.Sms(contact.number, sms))) {
            AgentResult("Abrí tu app de mensajes con el recado para ${contact.name}: \"${request.text}\". Toca enviar. Si tiene Ari, ella se lo dice.")
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
