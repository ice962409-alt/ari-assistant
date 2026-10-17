package com.ari.assistant.agents

/** Lo que el agente necesita del teléfono. En la app lo hace Android; en las pruebas, una lista. */
interface PhoneLauncher {
    fun installedApps(): List<InstalledApp>
    /** Abre la acción en el teléfono. false si ninguna app instalada sabe hacerla. */
    fun launch(action: PhoneAction, app: InstalledApp? = null): Boolean
    /** Los contactos con teléfono; null si el usuario aún no dio permiso. */
    fun contacts(): List<PhoneContact>?
    /** true si al marcar, Ari también toca "llamar" (solo la Ari CEO con Accesibilidad). */
    val callsDirectly: Boolean get() = false
    /** Guarda la cita directo en tu calendario (solo la Ari CEO con permiso). false si no se pudo. */
    fun saveEvent(title: String, start: java.time.LocalDateTime): Boolean = false
    /** true si Ari puede tocar "enviar" ella sola en WhatsApp (solo la Ari CEO con Accesibilidad). */
    val sendsDirectly: Boolean get() = false
    /** Justo antes de abrir el chat: deja armado el envío de exactamente [text] (ver AutoSend). */
    fun armSend(text: String, name: String) = Unit
    fun disarmSend() = Unit
}

/**
 * Agente del teléfono: abre apps y prepara acciones (marcar, WhatsApp,
 * alarmas, temporizadores, búsquedas, mapas, cámara, ajustes).
 * Sin Accesibilidad, el último paso (enviar, llamar) lo das tú en la app. Con ella (Ari CEO),
 * Ari envía el WhatsApp o llama sola, pero solo después de tu "sí" (decisión del 17/10/2026:
 * mensajes y llamadas siempre se confirman, y se puede contestar con la voz).
 */
class PhoneAgent(
    private val phone: PhoneLauncher,
    private val clock: () -> Long = System::currentTimeMillis
) : Agent {

    /** true cuando la última orden necesitaba los contactos y no hay permiso: la app lo pide. */
    @Volatile
    var needsContactsPermission = false

    /** Un WhatsApp o una llamada esperando tu "sí". */
    data class Pending(val action: PhoneAction, val name: String, val at: Long)

    /** A quién le falta el texto ("¿Qué le escribo a…?"): lo siguiente que digas es el mensaje. */
    data class Composing(val name: String, val number: String, val at: Long)

    @Volatile
    var pending: Pending? = null
        private set

    @Volatile
    var composing: Composing? = null
        private set

    /** ¿Hay una pregunta del agente esperando respuesta? (Las viejas se olvidan.) */
    fun waiting(): Boolean {
        val now = clock()
        if (pending?.let { now - it.at > WAIT_MS } == true) pending = null
        if (composing?.let { now - it.at > WAIT_MS } == true) composing = null
        return pending != null || composing != null
    }

    /**
     * ¿[text] contesta la pregunta pendiente? Al "¿qué le escribo?" cualquier cosa es el mensaje;
     * al "¿le mando…?" solo un sí o un no. Si es otra cosa, la pregunta se olvida y la frase
     * se atiende normal.
     */
    fun claims(text: String): Boolean {
        if (!waiting()) return false
        if (composing != null) return true
        if (Answers.yes(text) || Answers.no(text)) return true
        pending = null
        return false
    }

    /** Olvida cualquier pregunta pendiente (por ejemplo, si cambia quién habla). */
    fun forget() {
        pending = null
        composing = null
    }

    override val id = "telefono"
    override val name = "Agente del teléfono"
    override val description = "Abre tus apps y prepara llamadas, mensajes, alarmas y rutas para que tú confirmes."
    override val examples = listOf(
        "abre WhatsApp",
        "manda un WhatsApp al 55 1234 5678 que diga ya voy",
        "pon una alarma a las 7",
        "cómo llego al aeropuerto"
    )

    override fun match(message: String, normalized: String): AgentTask? {
        val action = PhoneCommands.parse(message) ?: return null
        return AgentTask(message, ACTION, message)
    }

    override fun execute(task: AgentTask): AgentResult {
        if (task.action == ANSWER) return answer(task.argument)
        // "confirmado": ya dijiste que sí (por ejemplo al "¿Lo hago?" de los pasos de Gemma).
        val confirmed = task.action == CONFIRMED
        val action = PhoneCommands.parse(task.argument)
            ?: return AgentResult("No entendí qué hacer en el teléfono.", success = false)

        if (action is PhoneAction.Missing) return AgentResult(action.message, success = false)

        if (action is PhoneAction.DialContact) return withContact(action.name) { contact ->
            call(contact.number, "${contact.name} (${contact.number})", confirmed)
        }
        if (action is PhoneAction.Dial && phone.callsDirectly) return call(action.number, "el ${action.number}", confirmed)

        if (action is PhoneAction.WhatsAppAsk) {
            forget()
            if (action.number.isNotEmpty()) {
                composing = Composing("el ${action.number}", action.number, clock())
                return AgentResult("¿Qué le escribo al ${action.number}?")
            }
            return withContact(action.name) { contact ->
                composing = Composing(contact.name, contact.number, clock())
                AgentResult("¿Qué le escribo a ${contact.name}?")
            }
        }
        if (action is PhoneAction.WhatsApp && action.number.isNotEmpty()) {
            return whatsApp(action.number, "el ${action.number}", action.text, confirmed)
        }

        if (action is PhoneAction.SmsContact) return withContact(action.name) { contact ->
            if (phone.launch(PhoneAction.Sms(contact.number, action.text)))
                AgentResult("Abrí un mensaje para ${contact.name} con el texto listo. Revísalo y toca enviar.")
            else AgentResult("No encontré una app de mensajes.", success = false)
        }

        if (action is PhoneAction.WhatsAppContact) return withContact(action.name) { contact ->
            whatsApp(contact.number, contact.name, action.text, confirmed)
        }

        if (action is PhoneAction.OpenApp) {
            return when (val choice = PhoneCommands.pickApp(action.name, phone.installedApps())) {
                is PhoneCommands.AppChoice.Found ->
                    if (phone.launch(action, choice.app)) AgentResult("Abriendo ${choice.app.label}.")
                    else AgentResult("No pude abrir ${choice.app.label}.", success = false)
                is PhoneCommands.AppChoice.Several -> AgentResult(
                    "Encontré varias: ${choice.apps.joinToString(", ") { it.label }}. ¿Cuál abro? Dime el nombre completo.",
                    success = false
                )
                PhoneCommands.AppChoice.None ->
                    AgentResult("No encontré una app llamada \"${action.name}\" en tu teléfono.", success = false)
            }
        }

        if (action is PhoneAction.CalendarEvent && phone.saveEvent(action.title, action.start)) {
            return AgentResult("Listo, agendé \"${action.title}\" para el ${describe(action.start)}.")
        }

        if (!phone.launch(action)) {
            return AgentResult(
                when (action) {
                    is PhoneAction.WhatsApp -> "No encontré WhatsApp en tu teléfono."
                    is PhoneAction.Alarm, is PhoneAction.Timer -> "Tu teléfono no tiene una app de reloj que acepte alarmas desde otras apps."
                    is PhoneAction.Navigate -> "No encontré una app de mapas."
                    is PhoneAction.PlayMusic ->
                        if (action.app.isNotEmpty()) "No encontré ${action.app} en tu teléfono." else "No encontré una app de música que acepte búsquedas."
                    is PhoneAction.CalendarEvent -> "No encontré una app de calendario."
                    is PhoneAction.Email -> "No encontré una app de correo."
                    is PhoneAction.Sms -> "No encontré una app de mensajes."
                    is PhoneAction.Ride -> if (action.app == "didi") "No encontré DiDi en tu teléfono." else "No encontré Uber ni un navegador."
                    is PhoneAction.Note -> "No encontré una app de notas."
                    is PhoneAction.SaveWifi -> "No pude abrir los ajustes de wifi."
                    else -> "No encontré una app que pueda hacer eso."
                },
                success = false
            )
        }
        return AgentResult(confirmation(action))
    }

    /** La respuesta a "¿Qué le escribo a…?" o a "¿Le mando…? Dime sí o no." */
    private fun answer(text: String): AgentResult {
        composing?.let { target ->
            composing = null
            if (Answers.no(text)) return AgentResult("Va, no le escribo.")
            val body = Answers.messageBody(text)
            if (body.isEmpty()) {
                composing = target.copy(at = clock())
                return AgentResult("No te entendí. ¿Qué le escribo ${to(target.name)}?", success = false)
            }
            return whatsApp(target.number, target.name, body, confirmed = false)
        }
        val waiting = pending ?: return AgentResult("Ya no tengo nada pendiente. Dímelo otra vez, por favor.", success = false)
        pending = null
        if (!Answers.yes(text)) {
            return AgentResult(if (waiting.action is PhoneAction.Dial) "Va, no llamo." else "Va, no lo mando.")
        }
        return when (val action = waiting.action) {
            is PhoneAction.WhatsApp -> openWhatsApp(action, waiting.name, send = true)
            is PhoneAction.Dial -> dial(action.number, waiting.name)
            else -> AgentResult("No pude hacerlo.", success = false)
        }
    }

    /** Con Accesibilidad, primero pregunta; sin ella, abre el chat y el último toque es tuyo. */
    private fun whatsApp(number: String, name: String, text: String, confirmed: Boolean): AgentResult {
        val action = PhoneAction.WhatsApp(number, text)
        if (phone.sendsDirectly && !confirmed) {
            forget()
            pending = Pending(action, name, clock())
            return AgentResult("¿Le mando ${to(name)} por WhatsApp: \"$text\"? Dime sí o no.")
        }
        return openWhatsApp(action, name, send = phone.sendsDirectly)
    }

    private fun openWhatsApp(action: PhoneAction.WhatsApp, name: String, send: Boolean): AgentResult {
        if (send) phone.armSend(action.text, name)
        if (phone.launch(action)) {
            return AgentResult(
                if (send) "Va, se lo mando ${to(name)}. Si en unos segundos no se envía, toca enviar."
                else "Abrí el chat ${of(name)} en WhatsApp con el mensaje listo. Revísalo y toca enviar."
            )
        }
        if (send) phone.disarmSend()
        // Sin WhatsApp, el mismo mensaje por SMS (ese lo envías tú).
        if (action.number.isNotEmpty() && phone.launch(PhoneAction.Sms(action.number, action.text))) {
            return AgentResult("No encontré WhatsApp: te dejé un SMS para $name con el texto listo. Revísalo y toca enviar.")
        }
        return AgentResult("No encontré WhatsApp en tu teléfono.", success = false)
    }

    /** Si Ari llama sola, primero pregunta. */
    private fun call(number: String, name: String, confirmed: Boolean): AgentResult {
        if (phone.callsDirectly && !confirmed) {
            forget()
            pending = Pending(PhoneAction.Dial(number), name, clock())
            return AgentResult("¿Llamo ${to(name)}? Dime sí o no.")
        }
        return dial(number, name)
    }

    private fun dial(number: String, name: String): AgentResult =
        if (phone.launch(PhoneAction.Dial(number)))
            AgentResult(
                if (phone.callsDirectly) "Llamando ${to(name)}…"
                else "Te dejé marcado ${to(name)}. Toca llamar para confirmar."
            )
        else AgentResult("No encontré una app de teléfono.", success = false)

    /** "a Juan", pero "al 5512345678" (los números se nombran "el 55…"). */
    private fun to(name: String) = if (name.startsWith("el ")) "al ${name.removePrefix("el ")}" else "a $name"

    private fun of(name: String) = if (name.startsWith("el ")) "del ${name.removePrefix("el ")}" else "de $name"

    /** Busca a [name] en los contactos; si no hay permiso, pide que la app lo solicite. */
    private fun withContact(name: String, act: (PhoneContact) -> AgentResult): AgentResult {
        val contacts = phone.contacts()
        if (contacts == null) {
            needsContactsPermission = true
            return AgentResult(
                "Para buscar a $name en tus contactos necesito tu permiso. Si aceptas, lo hago enseguida. " +
                    "Solo leo nombres y números, y no salen del teléfono.",
                success = false
            )
        }
        return when (val choice = PhoneCommands.pickContact(name, contacts)) {
            is PhoneCommands.ContactChoice.Found -> act(choice.contact)
            is PhoneCommands.ContactChoice.Several -> AgentResult(
                "Tienes varios contactos así: ${choice.contacts.joinToString(", ") { it.name }}. ¿A cuál? Dime el nombre completo.",
                success = false
            )
            PhoneCommands.ContactChoice.None ->
                AgentResult("No encontré a \"$name\" en tus contactos. También puedes decirme el número.", success = false)
        }
    }

    private fun confirmation(action: PhoneAction): String = when (action) {
        is PhoneAction.Dial ->
            if (phone.callsDirectly) "Llamando al ${action.number}…" else "Te dejé marcado el ${action.number}. Toca llamar para confirmar."
        is PhoneAction.WhatsApp ->
            if (action.number.isEmpty()) "Abrí WhatsApp con el mensaje listo: elige el chat y toca enviar."
            else "Abrí el chat de WhatsApp con el mensaje listo. Revísalo y toca enviar."
        is PhoneAction.Alarm -> {
            val time = "%d:%02d".format(action.hour, action.minute)
            "Abrí el reloj con una alarma a las $time" + (if (action.label.isNotEmpty()) " (${action.label})" else "") + ". Confírmala ahí si te lo pide."
        }
        is PhoneAction.Timer -> "Puse un temporizador de ${describeSeconds(action.seconds)}."
        is PhoneAction.WebSearch -> "Busqué \"${action.query}\" en tu navegador."
        is PhoneAction.Sms ->
            if (action.number.isEmpty()) "Abrí un mensaje con el texto listo: elige a quién y toca enviar."
            else "Abrí un mensaje para el ${action.number} con el texto listo. Revísalo y toca enviar."
        is PhoneAction.Email ->
            (if (action.to.isEmpty()) "Abrí un correo nuevo" else "Abrí un correo para ${action.to}") +
                (if (action.text.isNotEmpty()) " con el texto listo" else "") + ". Revísalo y toca enviar."
        is PhoneAction.PlayMusic ->
            "Busqué \"${action.query}\"" + (if (action.app.isNotEmpty()) " en ${appName(action.app)}" else " en tu app de música") + "."
        is PhoneAction.YouTube -> "Busqué \"${action.query}\" en YouTube."
        is PhoneAction.CalendarEvent ->
            "Abrí el calendario con \"${action.title}\" para el ${describe(action.start)}. Toca guardar para confirmarlo."
        is PhoneAction.Navigate -> "Abrí el mapa hacia ${action.place}."
        is PhoneAction.Ride ->
            if (action.app == "didi") "Abrí DiDi" + (if (action.place.isNotEmpty()) ": escribe el destino (${action.place}) y confirma." else ".")
            else "Abrí Uber" + (if (action.place.isNotEmpty()) " con el destino ${action.place}" else "") + ". Revisa y confirma el viaje ahí."
        is PhoneAction.Note -> "Abrí Keep con la nota lista. Toca guardar."
        is PhoneAction.SaveWifi ->
            if (action.ssid.isEmpty()) "Abrí los ajustes de wifi para que elijas la red."
            else "Abrí el diálogo para guardar el wifi \"${action.ssid}\". Toca Guardar para confirmar. Solo lo guardo con tu permiso."
        PhoneAction.Camera -> "Abrí la cámara."
        is PhoneAction.Settings -> "Abrí los ajustes" + (if (action.section != "general") " de ${action.section}" else "") + "."
        else -> "Listo."
    }

    private fun appName(app: String) = when (app) {
        "spotify" -> "Spotify"
        "youtube music" -> "YouTube Music"
        "deezer" -> "Deezer"
        else -> app
    }

    private fun describe(time: java.time.LocalDateTime): String =
        time.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' H:mm", java.util.Locale.forLanguageTag("es")))

    private fun describeSeconds(seconds: Int): String = when {
        seconds % 3600 == 0 -> plural(seconds / 3600, "hora", "horas")
        seconds % 60 == 0 -> plural(seconds / 60, "minuto", "minutos")
        else -> plural(seconds, "segundo", "segundos")
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

    companion object {
        private const val ACTION = "telefono"
        /** La tarea que contesta una pregunta pendiente del agente. */
        const val ANSWER = "respuesta"
        /** Una orden que ya confirmaste: no se vuelve a preguntar. */
        const val CONFIRMED = "confirmado"
        /** Cuánto espera Ari tu respuesta antes de olvidar la pregunta. */
        const val WAIT_MS = 2 * 60 * 1000L
    }
}
