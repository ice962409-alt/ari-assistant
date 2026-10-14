package com.ari.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/**
 * Recado por voz desde la burbuja: tocas un contacto de recados, Ari pregunta
 * "¿Qué le digo a Mamá?", lo dices y se abre tu app de mensajes con el recado listo.
 * No hace falta que Ari esté abierta ni que Gemma esté cargada.
 */
class DictateRecadoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val name = intent.getStringExtra(EXTRA_NAME)
        val number = intent.getStringExtra(EXTRA_NUMBER)
        if (name.isNullOrBlank() || number.isNullOrBlank()) {
            finish()
            return
        }
        val listen = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué le digo a ${name.substringBefore(' ')}?")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(listen, REQUEST)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Este teléfono no tiene reconocimiento de voz.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Uses the compatible activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        val number = intent.getStringExtra(EXTRA_NUMBER)
        if (requestCode == REQUEST && resultCode == RESULT_OK && !said.isNullOrEmpty() && number != null) {
            // "Que llego a las 8" → "llego a las 8".
            val text = said.replace(Regex("^(?i)(?:dile\\s+)?que\\s+"), "").replaceFirstChar { it.lowercase() }
            val owner = Profile.load(getSharedPreferences(Profile.PREFS, MODE_PRIVATE)).userName
            val body = AriMessages.encode(owner, text)
            if (AutoSend.enabled(this)) AutoSend.arm(body, intent.getStringExtra(EXTRA_NAME).orEmpty())
            val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
                .putExtra("sms_body", body)
            runCatching { startActivity(sms) }.onFailure {
                Toast.makeText(this, "No encontré una app de mensajes.", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_NAME = "nombre"
        const val EXTRA_NUMBER = "numero"
        private const val REQUEST = 1
    }
}
