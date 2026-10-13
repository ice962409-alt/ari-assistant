package com.ari.assistant

import com.ari.assistant.agents.Agent
import com.ari.assistant.agents.AriCeo

/**
 * Versión de Ari para otras personas: sin equipo de modelos. Solo la Ari del dueño
 * es la CEO; aquí no se buscan, contratan ni usan otros modelos, y Ari no habla
 * con ningún equipo de la red ni pide el permiso de ubicación.
 */
class CeoTeam(@Suppress("UNUSED_PARAMETER") host: CeoHost, @Suppress("UNUSED_PARAMETER") ceo: AriCeo) {

    val enabled = false

    fun isTeam(@Suppress("UNUSED_PARAMETER") agent: Agent): Boolean = false

    fun recruit() = Unit

    fun afterDelegation(@Suppress("UNUSED_PARAMETER") agent: Agent) = Unit

    fun withApprovedNetwork(@Suppress("UNUSED_PARAMETER") onAllowed: () -> Unit, onDenied: (String) -> Unit) =
        onDenied("Esta Ari no trabaja con un equipo de modelos.")

    fun onLocationResult() = Unit
}
