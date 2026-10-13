package com.ari.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Puente invisible del acceso directo "Hablar con Ari". Android abre los
 * accesos directos borrando la tarea de la app, y eso obligaría a Ari a cargar
 * de nuevo a Gemma (3.4 GB). Este puente vive en su propia tarea: le pide a
 * Ari que abra el modo live donde ya está y se cierra al instante.
 */
class LiveShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_LIVE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
