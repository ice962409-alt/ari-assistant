package com.ari.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * "Compartir → Ari" desde otras apps (Galería, WhatsApp, Archivos, el navegador…).
 *
 * Android solo le presta el archivo compartido a esta pantalla y solo mientras está
 * abierta, así que aquí mismo se copia a la carpeta temporal de Ari. Después Ari se abre
 * con el adjunto listo arriba del campo de texto: nada se envía hasta que tú lo decides.
 * Lo compartido es información, no órdenes para Ari.
 */
class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = sharedUri(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared == null && text.isEmpty()) {
            finish()
            return
        }
        Toast.makeText(this, "Preparando para Ari…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = try {
                Result.success(
                    if (shared != null) AttachmentReader.read(this, shared)
                    else AttachmentReader.fromText(intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "Texto compartido", text)
                )
            } catch (e: AttachmentReader.Unsupported) {
                Result.failure(e)
            } catch (e: Throwable) {
                Result.failure(Exception("No pude abrir lo que compartiste."))
            }
            runOnUiThread {
                result.onSuccess { attachment ->
                    handOver(attachment)
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_SHARED)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }.onFailure {
                    Toast.makeText(this, it.message ?: "No pude abrir lo que compartiste.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }.start()
    }

    private fun sharedUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        return intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    companion object {
        /** Lo compartido espera aquí, en la memoria de Ari, a que la pantalla principal lo tome. */
        @Volatile
        private var waiting: Attachment? = null

        private fun handOver(attachment: Attachment) {
            waiting?.delete()
            waiting = attachment
        }

        fun take(): Attachment? = waiting.also { waiting = null }
    }
}
