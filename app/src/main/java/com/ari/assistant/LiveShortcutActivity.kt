package com.ari.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock

/**
 * Puente invisible por el que entran al modo live el widget, el acceso directo
 * "Hablar con Ari" y el botón de ajustes rápidos.
 *
 * - Android abre los accesos directos borrando la tarea de la app, y eso obligaría
 *   a Ari a cargar de nuevo a Gemma (3.4 GB). Este puente vive en su propia tarea:
 *   le pide a Ari que abra el modo live donde ya está y se cierra al instante.
 * - Seguridad: el modo live enciende el micrófono. Como el puente no se puede abrir
 *   desde otras apps, deja un "boleto" en la memoria de Ari, y Ari solo abre el modo
 *   live si lo encuentra. Otra app que mande la orden directo a Ari no tiene boleto.
 */
class LiveShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ticketAt = SystemClock.elapsedRealtime()
        byVoice = intent?.getBooleanExtra(EXTRA_BY_VOICE, false) == true
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_LIVE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        /** El boleto vence en unos segundos para que no lo use una orden que llegue después. */
        private const val TICKET_MS = 10_000L

        /** Lo pone el detector de "Ari": la abriste con la voz, así que escucha sin saludar. */
        const val EXTRA_BY_VOICE = "por_voz"

        @Volatile
        private var ticketAt = 0L

        @Volatile
        private var byVoice = false

        /** true si el último boleto vino de decir "Ari" (se consume al leerlo). */
        fun consumeByVoice(): Boolean {
            val voice = byVoice
            byVoice = false
            return voice
        }

        /** true una sola vez por cada toque al widget o al acceso directo. */
        fun consumeTicket(): Boolean {
            val at = ticketAt
            ticketAt = 0L
            return at != 0L && SystemClock.elapsedRealtime() - at < TICKET_MS
        }
    }
}
