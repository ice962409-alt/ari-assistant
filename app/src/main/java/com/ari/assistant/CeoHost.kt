package com.ari.assistant

import android.app.Activity

/**
 * Lo que la parte de "CEO" (el equipo de modelos) necesita de la pantalla de Ari.
 * Solo la versión completa de Ari trae el equipo; la versión para otros no
 * incluye ese código (ver CeoTeam en src/completa y src/otros).
 */
interface CeoHost {
    val activity: Activity
    fun isBusy(): Boolean
    /** Ari dice algo en el chat (y se guarda). */
    fun say(reply: String)
    fun showStatus(text: String)
    /** Pide el permiso de ubicación; la respuesta llega a CeoTeam.onLocationResult. */
    fun requestLocation()
}
