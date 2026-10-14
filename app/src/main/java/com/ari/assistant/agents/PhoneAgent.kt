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
}

/**
 * Agente del teléfono: abre apps y prepara acciones (marcar, WhatsApp,
 * alarmas, temporizadores, búsquedas, mapas, cámara, ajustes). Nunca llama
 * ni envía nada por su cuenta: el último paso lo confirma el usuario en la app.
 */
class PhoneAgent(private val phone: PhoneLauncher) : Agent {

    /** true cuando la última orden necesitaba los contactos y no hay permiso: la app lo pide. */
    @Volatile
    var needsContactsPermission = false

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
        val action = PhoneCommands.parse(task.argument)
            ?: return AgentResult("No entendí qué hacer en el teléfono.", success = false)

        if (action is PhoneAction.Missing) return AgentResult(action.message, success = false)

        if (action is PhoneAction.DialContact) return withContact(action.name) { contact ->
            if (phone.launch(PhoneAction.Dial(contact.number)))
                AgentResult(
                    if (phone.callsDirectly) "Llamando a ${contact.name} (${contact.number})…"
                    else "Te dejé marcado a ${contact.name} (${contact.number}). Toca llamar para confirmar."
                )
            else AgentResult("No encontré una app de teléfono.", success = false)
        }

        if (action is PhoneAction.SmsContact) return withContact(action.name) { contact ->
            if (phone.launch(PhoneAction.Sms(contact.number, action.text)))
                AgentResult("Abrí un mensaje para ${contact.name} con el texto listo. Revísalo y toca enviar.")
            else AgentResult("No encontré una app de mensajes.", success = false)
        }

        if (action is PhoneAction.WhatsAppContact) return withContact(action.name) { contact ->
            if (phone.launch(PhoneAction.WhatsApp(contact.number, action.text)))
                AgentResult("Abrí el chat de ${contact.name} en WhatsApp con el mensaje listo. Revísalo y toca enviar.")
            else AgentResult("No encontré WhatsApp en tu teléfono.", success = false)
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
    }
}
