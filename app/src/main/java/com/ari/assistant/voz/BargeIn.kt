package com.ari.assistant.voz

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/**
 * ¿Le hablaste a Ari mientras ella hablaba? Compara el volumen del micrófono con lo que
 * se oye mientras Ari habla (su propia voz por la bocina): si de pronto hay mucha más voz
 * por un momento, eres tú. Sin Android, para poder probarlo.
 */
class BargeInDetector(
    private val sampleRate: Int = 16_000,
    /** Cuánto más fuerte que el fondo tiene que ser tu voz. */
    private val ratio: Double = 3.0,
    /** Cuánto tiempo seguido tienes que hablar (evita golpes y toses). */
    private val holdMs: Int = 350,
    /** Al principio solo escucha el fondo (la voz de Ari) para saber cómo suena. */
    private val learnMs: Int = 500
) {
    private var elapsed = 0
    private var loudMs = 0
    private var background = 0.0
    var triggered = false
        private set

    fun feed(samples: ShortArray, count: Int = samples.size): Boolean {
        if (triggered || count <= 0) return triggered
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        val rms = sqrt(sum / count)
        val ms = count * 1000 / sampleRate
        elapsed += ms
        if (elapsed <= learnMs) {
            background = if (background == 0.0) rms else background * 0.7 + rms * 0.3
            return false
        }
        val loud = rms > maxOf(MIN_VOICE, background * ratio)
        if (loud) {
            loudMs += ms
        } else {
            loudMs = maxOf(0, loudMs - ms)
            background = background * 0.95 + rms * 0.05
        }
        if (loudMs >= holdMs) triggered = true
        return triggered
    }

    private companion object {
        /** Debajo de esto no es una voz cercana (ruido del cuarto). */
        const val MIN_VOICE = 1500.0
    }
}

/**
 * Escucha el micrófono solo mientras Ari habla, con el cancelador de eco del teléfono,
 * y avisa en el hilo principal si la interrumpes. No graba ni guarda nada.
 */
class BargeInMonitor(private val onBargeIn: () -> Unit) {

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val rate = 16_000
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate / 5)
                )
            }.getOrNull()
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                running = false
                return@Thread
            }
            // Que el teléfono quite su propia voz (la de Ari) del micrófono, si sabe hacerlo.
            val echo = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
            val noise = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
            val detector = BargeInDetector(rate)
            val buffer = ShortArray(rate / 20)
            try {
                record.startRecording()
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    if (detector.feed(buffer, read)) {
                        running = false
                        main.post(onBargeIn)
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { record.stop() }
                record.release()
                echo?.release()
                noise?.release()
            }
        }.apply {
            name = "ari-interrumpir"
            start()
        }
    }

    /** Suelta el micrófono antes de que el reconocedor de voz lo pida (espera como mucho 0.2 s). */
    fun stop() {
        running = false
        val current = thread
        thread = null
        if (current != null && current !== Thread.currentThread()) runCatching { current.join(200) }
    }
}
