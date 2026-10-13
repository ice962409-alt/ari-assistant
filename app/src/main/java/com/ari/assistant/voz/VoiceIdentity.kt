package com.ari.assistant.voz

import android.content.Context
import android.os.Build
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

/**
 * Saca la huella de voz de una frase con un modelo pequeño (CAM++ de WeSpeaker, unos 29 MB)
 * que corre dentro del teléfono con sherpa-onnx, sin internet.
 */
class VoiceIdentity(private val context: Context) {

    @Volatile
    private var extractor: SpeakerEmbeddingExtractor? = null

    @Volatile
    private var broken = false

    /** Hace falta Android 13 (para pasarle el audio al reconocedor) y el modelo dentro de la app. */
    val supported: Boolean
        get() = !broken && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && modelPresent()

    private fun modelPresent() = runCatching { context.assets.open(MODEL).close(); true }.getOrDefault(false)

    /** La huella de [samples] (16 000 por segundo), o null si la frase es muy corta o algo falló. Es lento: fuera del hilo principal. */
    fun embedding(samples: FloatArray): FloatArray? {
        if (samples.size < MIN_SAMPLES) return null
        val active = ensure() ?: return null
        return try {
            val stream = active.createStream()
            try {
                stream.acceptWaveform(samples, VoiceCapture.RATE)
                stream.inputFinished()
                if (active.isReady(stream)) active.compute(stream) else null
            } finally {
                stream.release()
            }
        } catch (e: Throwable) {
            null
        }
    }

    @Synchronized
    private fun ensure(): SpeakerEmbeddingExtractor? {
        extractor?.let { return it }
        if (broken) return null
        return try {
            SpeakerEmbeddingExtractor(
                context.assets,
                SpeakerEmbeddingExtractorConfig(model = MODEL, numThreads = 2)
            ).also { extractor = it }
        } catch (e: Throwable) {
            // UnsatisfiedLinkError o modelo dañado: sin reconocimiento de voces, pero Ari sigue.
            broken = true
            null
        }
    }

    fun release() {
        extractor?.release()
        extractor = null
    }

    companion object {
        /** Lo pone GitHub Actions al compilar (no vive en el repositorio). */
        const val MODEL = "ari_voz.onnx"
        /** Al menos un segundo de voz para sacar una huella confiable. */
        private const val MIN_SAMPLES = 16_000
    }
}
