package com.ari.assistant

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Envío automático de recados (solo en la Ari CEO): justo antes de abrir tu app de mensajes
 * con un recado, Ari lo "arma". Su servicio de Accesibilidad (src/completa) toca "enviar" solo
 * si el recado está armado, es reciente y el texto en pantalla es exactamente ese recado.
 * En la Ari para otros no hay servicio, así que esto nunca hace nada.
 */
object AutoSend {

    /** Cuánto dura armado un recado. */
    const val VALID_MS = 20_000L

    data class Pending(val text: String, val name: String, val at: Long)

    @Volatile
    private var pending: Pending? = null

    fun arm(text: String, name: String) {
        pending = Pending(text, name, SystemClock.elapsedRealtime())
    }

    /** El recado armado si sigue vigente (sin quitarlo). */
    fun current(): Pending? = pending?.takeIf { SystemClock.elapsedRealtime() - it.at < VALID_MS }

    fun disarm() {
        pending = null
    }

    /** true si el servicio de envío automático está activado en Accesibilidad. */
    fun enabled(context: Context): Boolean {
        if (!context.resources.getBoolean(R.bool.ari_ceo)) return false
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return services.split(':').any { it.startsWith(context.packageName + "/") && it.endsWith("AriSendService") }
    }

    /** ¿El texto que hay en la caja de mensaje es el recado armado? (sin Android, para probarlo). */
    fun matches(onScreen: String, recado: String): Boolean {
        fun clean(text: String) = text.replace(Regex("\\s+"), " ").trim()
        return clean(onScreen) == clean(recado) && recado.trim().startsWith("[Ari")
    }
}
