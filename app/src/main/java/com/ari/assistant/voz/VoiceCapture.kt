package com.ari.assistant.voz

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ari graba ella misma una frase del micrófono. Con lo grabado hace dos cosas:
 * le pasa el audio al reconocedor de voz (para entender qué dices) y se queda con
 * una copia para sacar tu huella de voz (para saber quién habla). Al terminar, la
 * copia se usa una vez y se descarta: nunca se guarda una grabación.
 */
class VoiceCapture(
    /** Por aquí se le pasa el audio al reconocedor; null para solo grabar (al registrar una voz). */
    private val pipe: ParcelFileDescriptor?,
    private val onLevel: (Float) -> Unit,
    private val onFinished: (Result) -> Unit,
    private val detector: SpeechEndDetector = SpeechEndDetector()
) {
    class Result(
        /** La frase en números de -1 a 1, o null si no hablaste. */
        val samples: FloatArray?,
        /** true si el reconocedor nunca leyó el audio (no acepta audio de Ari). */
        val stalled: Boolean
    )

    private val running = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    @SuppressLint("MissingPermission") // Ari solo llama aquí con el permiso de micrófono concedido.
    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread({ record() }, "ari-microfono").start()
    }

    /** Termina ya (por ejemplo, si tocas a Ari o cierras el modo live). */
    fun stop() {
        stopped.set(true)
    }

    @SuppressLint("MissingPermission")
    private fun record() {
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK * 2 * 8)
            )
        } catch (e: Exception) {
            finish(null, stalled = false, writer = null)
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            finish(null, stalled = false, writer = null)
            return
        }

        val writer = pipe?.let { PipeWriter(ParcelFileDescriptor.AutoCloseOutputStream(it)) }
        val kept = ArrayList<ShortArray>()
        val preRoll = ArrayDeque<ShortArray>()
        val buffer = ShortArray(CHUNK)
        var stalled = false
        try {
            recorder.startRecording()
            while (!stopped.get()) {
                val read = recorder.read(buffer, 0, CHUNK)
                if (read <= 0) break
                val chunk = buffer.copyOf(read)
                writer?.send(chunk)
                if (writer != null && writer.backlog() > MAX_BACKLOG) {
                    // El reconocedor no está leyendo: este teléfono no acepta el audio de Ari.
                    stalled = true
                    break
                }
                val state = detector.feed(chunk, read)
                onLevel(detector.level)
                when (state) {
                    SpeechEndDetector.State.WAITING -> {
                        // Se guarda un poquito de antes, para no cortar la primera sílaba.
                        preRoll.addLast(chunk)
                        if (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
                    }
                    SpeechEndDetector.State.SPEAKING -> {
                        if (preRoll.isNotEmpty()) {
                            kept.addAll(preRoll)
                            preRoll.clear()
                        }
                        kept.add(chunk)
                    }
                    SpeechEndDetector.State.ENDED, SpeechEndDetector.State.NO_SPEECH -> break
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        val spoke = detector.state == SpeechEndDetector.State.ENDED ||
            (detector.state == SpeechEndDetector.State.SPEAKING && kept.isNotEmpty())
        finish(if (spoke && !stalled) toFloats(kept) else null, stalled, writer)
    }

    private fun finish(samples: FloatArray?, stalled: Boolean, writer: PipeWriter?) {
        // Cerrar el audio le dice al reconocedor "ya terminé de hablar".
        writer?.close()
        if (writer == null) runCatching { pipe?.close() }
        onFinished(Result(samples, stalled))
    }

    private fun toFloats(chunks: List<ShortArray>): FloatArray {
        val total = chunks.sumOf { it.size }
        val out = FloatArray(total)
        var i = 0
        chunks.forEach { chunk -> chunk.forEach { out[i++] = it / 32768f } }
        return out
    }

    /** Escribe al reconocedor en otro hilo, para que la grabación nunca se detenga esperándolo. */
    private class PipeWriter(private val out: FileOutputStream) {
        private val queue = LinkedBlockingQueue<ShortArray>()
        private val pending = AtomicInteger(0)
        private val thread = Thread({ loop() }, "ari-microfono-salida").apply {
            isDaemon = true
            start()
        }

        fun send(chunk: ShortArray) {
            pending.incrementAndGet()
            queue.put(chunk)
        }

        fun backlog() = pending.get()

        fun close() {
            queue.put(END)
        }

        private fun loop() {
            val bytes = ByteArray(CHUNK * 2)
            try {
                while (true) {
                    val chunk = queue.take()
                    if (chunk === END) break
                    for (i in chunk.indices) {
                        bytes[i * 2] = (chunk[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (chunk[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    out.write(bytes, 0, chunk.size * 2)
                    pending.decrementAndGet()
                }
            } catch (_: Exception) {
            } finally {
                runCatching { out.close() }
            }
        }

        companion object {
            private val END = ShortArray(0)
        }
    }

    companion object {
        const val RATE = 16_000
        /** 20 ms de audio por pedazo. */
        private const val CHUNK = 320
        private const val PRE_ROLL_CHUNKS = 15
        /** Si el reconocedor lleva 3 segundos sin leer, no acepta el audio de Ari. */
        private const val MAX_BACKLOG = 150
    }
}
