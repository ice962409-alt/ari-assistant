package com.ari.assistant

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Envío automático (solo en la Ari CEO): justo antes de abrir tu app de mensajes con un recado,
 * o WhatsApp con un mensaje que confirmaste con tu "sí", Ari lo "arma". Su servicio de
 * Accesibilidad (src/completa) toca "enviar" solo si está armado, es reciente y el texto en
 * pantalla es exactamente ese. En la Ari para otros no hay servicio, así que esto nunca hace nada.
 */
object AutoSend {

    /** Cuánto dura armado un recado. */
    const val VALID_MS = 20_000L

    /** [confirmed]: un WhatsApp que el dueño aprobó con su "sí" (no lleva la marca "[Ari"). */
    data class Pending(val text: String, val name: String, val at: Long, val confirmed: Boolean = false)

    @Volatile
    private var pending: Pending? = null

    fun arm(text: String, name: String) {
        pending = Pending(text, name, SystemClock.elapsedRealtime())
    }

    /** Un WhatsApp que confirmaste: se envía solo si en la caja está exactamente [text]. */
    fun armConfirmed(text: String, name: String) {
        pending = Pending(text, name, SystemClock.elapsedRealtime(), confirmed = true)
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

    /**
     * ¿El texto que hay en la caja de mensaje es el armado? Un recado lleva la marca "[Ari";
     * un WhatsApp confirmado solo tiene que ser igual (sin Android, para probarlo).
     */
    fun matches(onScreen: String, recado: String, confirmed: Boolean = false): Boolean {
        fun clean(text: String) = text.replace(Regex("\\s+"), " ").trim()
        if (clean(recado).isEmpty() || clean(onScreen) != clean(recado)) return false
        return confirmed || recado.trim().startsWith("[Ari")
    }
}
