package com.ari.assistant.voz

import kotlin.math.sqrt

/**
 * Decide, por el volumen, cuándo empezaste a hablar y cuándo terminaste.
 * Cuando Ari graba ella misma el micrófono (para reconocer quién habla), el
 * reconocedor de voz ya no sabe solo cuándo cortar: esto le avisa.
 * Sin Android, para poder probarlo.
 */
class SpeechEndDetector(
    private val sampleRate: Int = 16_000,
    /** Silencio que cuenta como "terminó de hablar" (como en una plática). */
    private val silenceMs: Int = 900,
    /** Si no habla en este tiempo, se da por no dicho. */
    private val noSpeechMs: Int = 6_000,
    /** Lo más largo que puede durar una frase. */
    private val maxMs: Int = 15_000,
    /** Voz mínima para contar como frase (evita toses y ruidos sueltos). */
    private val minSpeechMs: Int = 250
) {
    enum class State { WAITING, SPEAKING, ENDED, NO_SPEECH }

    var state = State.WAITING
        private set

    /** Volumen de 0 a 1 del último pedazo, para que la esfera brille con tu voz. */
    var level = 0f
        private set

    private var elapsed = 0
    private var voiced = 0
    private var silent = 0
    // El ruido de fondo se aprende al principio y se va ajustando poco a poco.
    private var noise = 0.0
    private var noiseSamples = 0

    fun feed(samples: ShortArray, count: Int = samples.size): State {
        if (state == State.ENDED || state == State.NO_SPEECH || count <= 0) return state
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        val rms = sqrt(sum / count)
        val ms = count * 1000 / sampleRate
        elapsed += ms
        level = (rms / 3000.0).coerceIn(0.0, 1.0).toFloat()

        if (noiseSamples < NOISE_LEARN_MS) {
            noise = if (noiseSamples == 0) rms else noise * 0.8 + rms * 0.2
            noiseSamples += ms
        }
        val threshold = maxOf(MIN_THRESHOLD, noise * 2.5)
        val loud = rms > threshold

        when (state) {
            State.WAITING -> {
                if (loud) voiced += ms else voiced = maxOf(0, voiced - ms)
                if (!loud) noise = noise * 0.98 + rms * 0.02
                if (voiced >= minSpeechMs) {
                    state = State.SPEAKING
                    silent = 0
                } else if (elapsed >= noSpeechMs) {
                    state = State.NO_SPEECH
                }
            }
            State.SPEAKING -> {
                if (loud) silent = 0 else silent += ms
                if (silent >= silenceMs || elapsed >= maxMs) state = State.ENDED
            }
            else -> Unit
        }
        return state
    }

    companion object {
        private const val NOISE_LEARN_MS = 300
        private const val MIN_THRESHOLD = 450.0
    }
}
