package com.ari.assistant

import android.app.AlertDialog
import android.content.Context
import com.ari.assistant.agents.Agent
import com.ari.assistant.agents.AriCeo
import com.ari.assistant.agents.NetworkGuard
import com.ari.assistant.agents.TeamAgent

/**
 * Ari CEO (solo en la versión completa, la del dueño): busca modelos en la red,
 * pregunta a quién contratar y les pasa tareas, siempre en el hotspot del teléfono
 * o en redes Wi-Fi aprobadas. Nunca les pasa tus recuerdos.
 */
class CeoTeam(private val host: CeoHost, private val ceo: AriCeo) {

    val enabled = true

    private val settings = host.activity.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    private val guard = NetworkGuard(host.activity, settings)
    private val team = TeamAgent(settings, guard)

    // Acción con el equipo que espera el permiso de ubicación.
    private var pendingNetworkAction: (() -> Unit)? = null

    init {
        ceo.hire(team)
    }

    fun isTeam(agent: Agent): Boolean = agent === team

    /**
     * Autonomía del CEO: si aún no tiene equipo en esta red, busca modelos.
     * Solo en redes aprobadas; contratarlos lo decide el usuario.
     */
    fun recruit() {
        val network = guard.current()
        if (!guard.isApproved(network) || team.hasTeam(network)) return

        ceo.run(AriCeo.Delegation(team, team.discoveryTask())) { result ->
            if (host.activity.isDestroyed || !result.success) return@run
            if (host.isBusy()) host.showStatus(result.reply) else host.say(result.reply)
            offerCandidates()
        }
    }

    /** Después de que el equipo trabajó, revisa si encontró modelos nuevos. */
    fun afterDelegation(agent: Agent) {
        if (agent === team) offerCandidates()
    }

    /** Si Ari encontró modelos nuevos, pregunta al usuario a quién contratar. */
    private fun offerCandidates() {
        val network = guard.current()
        val found = team.candidates(network)
        val activity = host.activity
        if (found.isEmpty() || activity.isFinishing || activity.isDestroyed) return

        val labels = found.map { "${it.role.title}: ${it.model}\n${it.url}" }.toTypedArray()
        val checked = BooleanArray(found.size)
        AlertDialog.Builder(activity)
            .setTitle("¿A quién contrato? Elige solo equipos que reconozcas")
            .setMultiChoiceItems(labels, checked) { _, index, isChecked -> checked[index] = isChecked }
            .setPositiveButton("Contratar") { _, _ ->
                val chosen = found.filterIndexed { index, _ -> checked[index] }
                team.hireCandidates(network, chosen)
                host.say(
                    if (chosen.isEmpty()) {
                        "No contraté a nadie."
                    } else {
                        "Contraté a ${chosen.joinToString { it.label }}. " +
                            "Solo reciben las tareas que les paso, nunca tus recuerdos."
                    }
                )
            }
            .setNegativeButton("A nadie") { _, _ -> team.rejectCandidates(network) }
            .setCancelable(false)
            .show()
    }

    /**
     * Ejecuta [onAllowed] solo en el hotspot del teléfono o en una red Wi-Fi
     * aprobada. Si hace falta, pide el permiso de ubicación (para leer el
     * nombre de la red) o la aprobación de la red.
     */
    fun withApprovedNetwork(onAllowed: () -> Unit, onDenied: (String) -> Unit) {
        when (val network = guard.current()) {
            is NetworkGuard.Network.Hotspot -> onAllowed()

            is NetworkGuard.Network.Wifi -> {
                if (guard.isApproved(network)) {
                    onAllowed()
                    return
                }
                if (network.open) {
                    onDenied(
                        "La red «${network.name}» no tiene contraseña, así que no trabajo con el equipo ahí. " +
                            "Activa el hotspot de tu teléfono y conecta la laptop a él."
                    )
                    return
                }
                AlertDialog.Builder(host.activity)
                    .setTitle("¿Trabajar con el equipo en esta red?")
                    .setMessage(
                        "Ari hablará con los modelos de tu equipo por la red Wi-Fi «${network.name}» y les pasará " +
                            "las tareas que les encargue, sin cifrar (tus recuerdos nunca salen del teléfono). " +
                            "Apruébala solo si confías en quienes " +
                            "están conectados: tu casa o una red privada. En redes públicas usa el " +
                            "hotspot de tu teléfono."
                    )
                    .setPositiveButton("Aprobar") { _, _ ->
                        guard.approve(network)
                        onAllowed()
                    }
                    .setNegativeButton("No") { _, _ ->
                        onDenied("De acuerdo, no trabajaré con el equipo en la red «${network.name}».")
                    }
                    .setCancelable(false)
                    .show()
            }

            is NetworkGuard.Network.UnknownWifi -> {
                if (!guard.hasLocationPermission()) {
                    pendingNetworkAction = { withApprovedNetwork(onAllowed, onDenied) }
                    host.requestLocation()
                } else {
                    onDenied(
                        "No puedo leer el nombre de la red Wi-Fi. Enciende la ubicación del teléfono " +
                            "y vuelve a intentarlo."
                    )
                }
            }

            is NetworkGuard.Network.None ->
                onDenied("El teléfono no está conectado a ninguna red. Conéctate a tu Wi-Fi o activa el hotspot.")
        }
    }

    fun onLocationResult() {
        val action = pendingNetworkAction
        pendingNetworkAction = null
        // Android solo deja leer el nombre de la Wi-Fi con la ubicación precisa;
        // con la aproximada no se vuelve a preguntar (evita un ciclo de permisos).
        if (guard.hasLocationPermission()) {
            action?.invoke()
        } else {
            host.showStatus("Ari necesita la ubicación precisa (no la aproximada) para reconocer la red Wi-Fi.")
        }
    }

    private companion object {
        const val SETTINGS = "ari_agentes"
    }
}
