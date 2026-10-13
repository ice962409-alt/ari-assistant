package com.ari.assistant.agents

/** Lo que el agente necesita del teléfono. En la app lo hace Android; en las pruebas, una lista. */
interface PhoneLauncher {
    fun installedApps(): List<InstalledApp>
    /** Abre la acción en el teléfono. false si ninguna app instalada sabe hacerla. */
    fun launch(action: PhoneAction, app: InstalledApp? = null): Boolean
}

/**
 * Agente del teléfono: abre apps y prepara acciones (marcar, WhatsApp,
 * alarmas, temporizadores, búsquedas, mapas, cámara, ajustes). Nunca llama
 * ni envía nada por su cuenta: el último paso lo confirma el usuario en la app.
 */
class PhoneAgent(private val phone: PhoneLauncher) : Agent {

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

        if (!phone.launch(action)) {
            return AgentResult(
                when (action) {
                    is PhoneAction.WhatsApp -> "No encontré WhatsApp en tu teléfono."
                    is PhoneAction.Alarm, is PhoneAction.Timer -> "Tu teléfono no tiene una app de reloj que acepte alarmas desde otras apps."
                    is PhoneAction.Navigate -> "No encontré una app de mapas."
                    else -> "No encontré una app que pueda hacer eso."
                },
                success = false
            )
        }
        return AgentResult(confirmation(action))
    }

    private fun confirmation(action: PhoneAction): String = when (action) {
        is PhoneAction.Dial -> "Te dejé marcado el ${action.number}. Toca llamar para confirmar."
        is PhoneAction.WhatsApp ->
            if (action.number.isEmpty()) "Abrí WhatsApp con el mensaje listo: elige el chat y toca enviar."
            else "Abrí el chat de WhatsApp con el mensaje listo. Revísalo y toca enviar."
        is PhoneAction.Alarm -> {
            val time = "%d:%02d".format(action.hour, action.minute)
            "Abrí el reloj con una alarma a las $time" + (if (action.label.isNotEmpty()) " (${action.label})" else "") + ". Confírmala ahí si te lo pide."
        }
        is PhoneAction.Timer -> "Puse un temporizador de ${describeSeconds(action.seconds)}."
        is PhoneAction.WebSearch -> "Busqué \"${action.query}\" en tu navegador."
        is PhoneAction.Navigate -> "Abrí el mapa hacia ${action.place}."
        PhoneAction.Camera -> "Abrí la cámara."
        is PhoneAction.Settings -> "Abrí los ajustes" + (if (action.section != "general") " de ${action.section}" else "") + "."
        else -> "Listo."
    }

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
