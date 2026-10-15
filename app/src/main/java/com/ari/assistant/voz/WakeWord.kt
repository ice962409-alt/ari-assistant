package com.ari.assistant.voz

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Ari" personal: Ari aprende cómo dices tú su nombre (lo dices 3 veces) y después
 * compara cada palabra suelta que oye con esas 3 muestras.
 *
 * - No usa internet ni un modelo de voz: compara la "forma" del sonido (MFCC) con
 *   una regla que estira o encoge el tiempo (DTW), por si lo dices más rápido o lento.
 * - Solo guarda esas formas (números), nunca la grabación.
 * - Sin Android, para poder probarlo.
 */
object WakeWord {
    const val RATE = 16_000
    private const val FRAME = 400 // 25 ms
    private const val HOP = 160 // 10 ms
    private const val FFT = 512
    private const val MELS = 26
    private const val CEPS = 13

    private val window = DoubleArray(FRAME) { 0.54 - 0.46 * cos(2 * PI * it / (FRAME - 1)) }
    private val filters: Array<DoubleArray> = melFilters()
    private val dct = Array(CEPS) { i -> DoubleArray(MELS) { j -> cos(PI * i * (j + 0.5) / MELS) } }

    /** La forma del sonido: 12 números cada 10 ms (sin el volumen, que cambia según la distancia). */
    fun features(samples: FloatArray): Array<FloatArray> {
        if (samples.size < FRAME) return emptyArray()
        val x = DoubleArray(samples.size)
        x[0] = samples[0].toDouble()
        for (i in 1 until samples.size) x[i] = samples[i] - 0.97 * samples[i - 1]
        val count = (x.size - FRAME) / HOP + 1
        val re = DoubleArray(FFT)
        val im = DoubleArray(FFT)
        val power = DoubleArray(FFT / 2 + 1)
        return Array(count) { f ->
            re.fill(0.0)
            im.fill(0.0)
            val start = f * HOP
            for (i in 0 until FRAME) re[i] = x[start + i] * window[i]
            fft(re, im)
            for (k in power.indices) power[k] = (re[k] * re[k] + im[k] * im[k]) / FFT
            val logMel = DoubleArray(MELS) { m ->
                var sum = 0.0
                val filter = filters[m]
                for (k in filter.indices) sum += filter[k] * power[k]
                ln(sum + 1e-10)
            }
            FloatArray(CEPS - 1) { c ->
                var sum = 0.0
                val row = dct[c + 1]
                for (j in 0 until MELS) sum += row[j] * logMel[j]
                sum.toFloat()
            }
        }
    }

    /** Quita el "color" del micrófono: resta el promedio de los primeros [frames] cuadros. */
    fun normalized(features: Array<FloatArray>, frames: Int = features.size): Array<FloatArray> {
        if (features.isEmpty()) return features
        val n = frames.coerceIn(1, features.size)
        val dims = features[0].size
        val mean = FloatArray(dims)
        for (i in 0 until n) for (d in 0 until dims) mean[d] += features[i][d] / n
        return Array(features.size) { i -> FloatArray(dims) { d -> features[i][d] - mean[d] } }
    }

    /** Recorta el silencio de antes y de después de la palabra. */
    fun trim(samples: FloatArray): FloatArray {
        val blocks = samples.size / HOP
        if (blocks == 0) return samples
        val energy = DoubleArray(blocks) { b ->
            var sum = 0.0
            for (i in b * HOP until (b + 1) * HOP) sum += samples[i] * samples[i]
            sqrt(sum / HOP)
        }
        val peak = energy.maxOrNull() ?: 0.0
        val threshold = max(peak * 0.05, 1e-3)
        val first = energy.indexOfFirst { it > threshold }
        val last = energy.indexOfLast { it > threshold }
        if (first < 0) return FloatArray(0)
        val from = max(0, first - 5) * HOP
        val to = min(samples.size, (last + 6) * HOP)
        return samples.copyOfRange(from, to)
    }

    /**
     * Qué tan distinta es una palabra de una muestra: 0 = idéntica; "Ari" contra "Ari"
     * suele quedar abajo de 0.12 y otras palabras arriba de 0.15. La palabra puede
     * durar de 0.6 a 1.6 veces lo que dura la muestra.
     */
    fun distance(template: Array<FloatArray>, heard: Array<FloatArray>): Double {
        val n = template.size
        val m = heard.size
        if (n == 0 || m < (0.6 * n).toInt().coerceAtLeast(1)) return Double.POSITIVE_INFINITY
        val a = unit(template)
        val b = unit(heard)
        val inf = Double.POSITIVE_INFINITY
        var previous = DoubleArray(m + 1) { inf }.also { it[0] = 0.0 }
        var current = DoubleArray(m + 1)
        for (i in 1..n) {
            current.fill(inf)
            val from = max(1, i / 2)
            val to = min(m, 2 * i + 5)
            for (j in from..to) {
                var dot = 0.0
                val x = a[i - 1]
                val y = b[j - 1]
                for (d in x.indices) dot += x[d] * y[d]
                val cost = 1.0 - dot
                current[j] = cost + minOf(previous[j], current[j - 1], previous[j - 1])
            }
            val swap = previous
            previous = current
            current = swap
        }
        var best = inf
        val lo = max(1, (0.6 * n).toInt())
        val hi = min(m, (1.6 * n).toInt())
        for (j in lo..hi) best = min(best, previous[j] / (n + j))
        return best
    }

    private fun unit(features: Array<FloatArray>) = Array(features.size) { i ->
        val v = features[i]
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum).takeIf { it > 1e-9 } ?: 1.0
        DoubleArray(v.size) { v[it] / norm }
    }

    private fun melFilters(): Array<DoubleArray> {
        fun mel(f: Double) = 2595 * log10(1 + f / 700)
        fun hz(m: Double) = 700 * (10.0.pow(m / 2595) - 1)
        val low = mel(60.0)
        val high = mel(7600.0)
        val bins = IntArray(MELS + 2) { i ->
            val f = hz(low + (high - low) * i / (MELS + 1))
            ((FFT + 1) * f / RATE).toInt()
        }
        return Array(MELS) { m ->
            val l = bins[m]
            val c = bins[m + 1]
            val r = bins[m + 2]
            DoubleArray(FFT / 2 + 1) { k ->
                when {
                    k in l until c -> (k - l).toDouble() / max(1, c - l)
                    k in c until r -> (r - k).toDouble() / max(1, r - c)
                    else -> 0.0
                }
            }
        }
    }

    /** Transformada rápida de Fourier (tamaño potencia de 2), en el mismo arreglo. */
    internal fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            var start = 0
            while (start < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = start + k
                    val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                start += len
            }
            len = len shl 1
        }
    }
}

/**
 * Las 3 muestras de cómo dices "Ari" y el límite para aceptar una palabra.
 * El límite sale de qué tan parecidas son tus 3 muestras entre sí.
 */
class WakeWordModel(val templates: List<Array<FloatArray>>, val threshold: Double) {

    /** La distancia más corta a alguna de tus muestras. */
    fun score(samples: FloatArray): Double {
        val raw = WakeWord.features(WakeWord.trim(samples))
        if (raw.isEmpty()) return Double.POSITIVE_INFINITY
        return templates.minOf { WakeWord.distance(it, WakeWord.normalized(raw, (it.size * 1.2).toInt())) }
    }

    fun matches(samples: FloatArray) = score(samples) <= threshold

    companion object {
        const val MIN_THRESHOLD = 0.10
        const val MAX_THRESHOLD = 0.20

        /**
         * Arma el modelo con tus grabaciones de "Ari". null si son menos de 2 o si no se
         * parecen entre sí (por ejemplo, si hubo ruido o dijiste otra cosa).
         */
        fun learn(recordings: List<FloatArray>, sensitivity: Double = 1.0): WakeWordModel? {
            val all = recordings
                .map { WakeWord.features(WakeWord.trim(it)) }
                .filter { it.size >= 15 } // al menos 0.15 s de voz
                .map { WakeWord.normalized(it) }
            if (all.size < 2) return null
            // Con 4 o más muestras, descarta la más distinta (ruido o un "Ari" raro): así una
            // grabación mala no empeora el reconocimiento ni infla el límite.
            val templates = if (all.size >= 4) dropOutlier(all) else all
            val pairs = mutableListOf<Double>()
            for (i in templates.indices) for (j in templates.indices) {
                if (i != j) pairs += WakeWord.distance(templates[i], templates[j])
            }
            val mean = pairs.average()
            if (mean.isNaN() || mean > MAX_THRESHOLD) return null
            val threshold = (mean * 1.7 * sensitivity).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
            return WakeWordModel(templates, threshold)
        }

        /** Quita la muestra cuya distancia promedio a las demás es la mayor. */
        internal fun dropOutlier(templates: List<Array<FloatArray>>): List<Array<FloatArray>> {
            val avg = DoubleArray(templates.size)
            for (i in templates.indices) {
                var sum = 0.0
                for (j in templates.indices) if (i != j) sum += WakeWord.distance(templates[i], templates[j])
                avg[i] = sum / (templates.size - 1)
            }
            val worst = avg.indices.maxByOrNull { avg[it] } ?: return templates
            return templates.filterIndexed { index, _ -> index != worst }
        }
    }
}

/**
 * Encuentra palabras sueltas en el micrófono: voz corta (0.2 a 1.3 s) con silencio
 * antes y después. Frases largas no cuentan: el nombre se dice solo, como al llamar
 * a alguien. Así Ari casi no hace cuentas mientras nadie la llama.
 */
class WakeSegmenter(
    private val rate: Int = WakeWord.RATE,
    private val minMs: Int = 200,
    private val maxMs: Int = 1_300,
    private val silenceMs: Int = 350
) {
    private val preRoll = rate / 5 // 0.2 s antes de que empiece la voz
    private val ring = FloatArray(preRoll)
    private var ringPos = 0
    private var ringFull = false
    private var word: FloatArrayBuilder? = null
    private var voicedMs = 0
    private var silentMs = 0
    private var tooLong = false
    private var noise = 0.0
    private var learnedMs = 0

    /** Le das un pedazo del micrófono; devuelve la palabra suelta si acaba de terminar una. */
    fun feed(samples: ShortArray, count: Int = samples.size): FloatArray? {
        if (count <= 0) return null
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        val rms = sqrt(sum / count)
        val ms = count * 1000 / rate
        if (learnedMs < 300) {
            noise = if (learnedMs == 0) rms else noise * 0.8 + rms * 0.2
            learnedMs += ms
        }
        val loud = rms > max(MIN_LEVEL, noise * 2.5)
        if (!loud && word == null) noise = noise * 0.98 + rms * 0.02

        val current = word
        if (current == null) {
            if (loud) {
                val started = FloatArrayBuilder()
                // Lo que se oyó justo antes, para no cortar el principio de "Ari".
                if (ringFull) for (i in 0 until preRoll) started.add(ring[(ringPos + i) % preRoll])
                else for (i in 0 until ringPos) started.add(ring[i])
                append(started, samples, count)
                word = started
                voicedMs = ms
                silentMs = 0
                tooLong = false
            } else {
                remember(samples, count)
            }
            return null
        }
        append(current, samples, count)
        if (loud) {
            voicedMs += ms + silentMs
            silentMs = 0
            if (voicedMs > maxMs) tooLong = true
        } else {
            silentMs += ms
        }
        if (silentMs < silenceMs) return null
        // Terminó: solo cuenta si fue corta y no venía pegada a más plática.
        word = null
        clearRing()
        val result = if (!tooLong && voicedMs >= minMs) current.build() else null
        remember(samples, count)
        return result
    }

    fun reset() {
        word = null
        clearRing()
    }

    private fun clearRing() {
        ringPos = 0
        ringFull = false
    }

    private fun remember(samples: ShortArray, count: Int) {
        for (i in 0 until count) {
            ring[ringPos] = samples[i] / 32768f
            ringPos = (ringPos + 1) % preRoll
            if (ringPos == 0) ringFull = true
        }
    }

    private fun append(builder: FloatArrayBuilder, samples: ShortArray, count: Int) {
        for (i in 0 until count) builder.add(samples[i] / 32768f)
    }

    private class FloatArrayBuilder {
        private var data = FloatArray(16_000)
        private var size = 0
        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }
        fun build(): FloatArray = data.copyOf(size)
    }

    private companion object {
        const val MIN_LEVEL = 450.0
    }
}
