package com.ari.assistant.voz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class WakeWordTest {

    /** Una "palabra" de prueba: un tono con dos resonancias que se deslizan (como "a" → "i"). */
    private fun word(seconds: Double, rising: Boolean = true, seed: Int = 1, pitch: Double = 120.0): FloatArray {
        val random = Random(seed)
        val n = (WakeWord.RATE * seconds).toInt()
        val pad = 3_200
        val out = FloatArray(n + 2 * pad) { (random.nextDouble(-1.0, 1.0) * 0.003).toFloat() }
        var p0 = 0.0
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val f1 = if (rising) 700 - 400 * p else 300 + 400 * p
            val f2 = if (rising) 1200 + 1100 * p else 2300 - 1100 * p
            p0 += 2 * PI * pitch / WakeWord.RATE
            p1 += 2 * PI * f1 / WakeWord.RATE
            p2 += 2 * PI * f2 / WakeWord.RATE
            val env = sin(PI * p).pow(0.5)
            out[pad + i] += (env * (0.5 * sin(p0) + 0.3 * sin(p1) + 0.2 * sin(p2)) * 0.3).toFloat()
        }
        return out
    }

    @Test
    fun fftOfAnImpulseIsFlat() {
        val re = DoubleArray(8).also { it[0] = 1.0 }
        val im = DoubleArray(8)
        WakeWord.fft(re, im)
        re.forEach { assertEquals(1.0, it, 1e-9) }
        im.forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test
    fun featuresAreTwelveNumbersEvery10ms() {
        val features = WakeWord.features(FloatArray(16_000) { sin(it * 0.1).toFloat() })
        assertEquals((16_000 - 400) / 160 + 1, features.size)
        assertEquals(12, features[0].size)
        assertEquals(0, WakeWord.features(FloatArray(100)).size)
    }

    @Test
    fun aWordIsAtDistanceZeroFromItself() {
        val f = WakeWord.normalized(WakeWord.features(WakeWord.trim(word(0.5))))
        assertEquals(0.0, WakeWord.distance(f, f), 1e-6)
    }

    @Test
    fun trimRemovesTheSilence() {
        val trimmed = WakeWord.trim(word(0.5))
        assertTrue(trimmed.size < word(0.5).size)
        assertTrue(trimmed.size >= (0.5 * WakeWord.RATE).toInt() * 8 / 10)
        assertEquals(0, WakeWord.trim(FloatArray(4_000)).size)
    }

    @Test
    fun learnsTheWordAndRecognizesItFasterOrSlower() {
        val model = WakeWordModel.learn(listOf(word(0.45, seed = 1), word(0.5, seed = 2), word(0.55, seed = 3)))
        assertNotNull(model)
        model!!
        assertTrue(model.threshold in WakeWordModel.MIN_THRESHOLD..WakeWordModel.MAX_THRESHOLD)
        assertTrue(model.matches(word(0.48, seed = 7)))
        assertTrue(model.matches(word(0.4, seed = 8)))
        assertTrue(model.matches(word(0.5, seed = 10, pitch = 200.0)))
    }

    @Test
    fun rejectsOtherSounds() {
        val model = WakeWordModel.learn(listOf(word(0.45, seed = 1), word(0.5, seed = 2), word(0.55, seed = 3)))!!
        assertFalse(model.matches(word(0.5, rising = false, seed = 9)))
        val random = Random(3)
        assertFalse(model.matches(FloatArray(12_000) { (random.nextDouble(-1.0, 1.0) * 0.08).toFloat() }))
        assertFalse(model.matches(FloatArray(8_000)))
    }

    @Test
    fun needsAtLeastTwoGoodRecordings() {
        assertNull(WakeWordModel.learn(listOf(word(0.5))))
        assertNull(WakeWordModel.learn(listOf(word(0.5), FloatArray(8_000))))
        // Dos cosas que no se parecen: mejor volver a grabar.
        assertNull(WakeWordModel.learn(listOf(word(0.5, rising = true), word(0.5, rising = false))))
    }

    private fun feedAll(segmenter: WakeSegmenter, levels: List<Pair<Int, Int>>): List<FloatArray> {
        // levels: (amplitud, milisegundos), en pedazos de 50 ms.
        val found = mutableListOf<FloatArray>()
        for ((amplitude, ms) in levels) {
            repeat(ms / 50) {
                val chunk = ShortArray(800) { i -> (amplitude * sin(i * 0.3)).toInt().toShort() }
                segmenter.feed(chunk)?.let { found += it }
            }
        }
        return found
    }

    @Test
    fun findsAShortWordAlone() {
        val words = feedAll(WakeSegmenter(), listOf(100 to 500, 5_000 to 500, 100 to 500))
        assertEquals(1, words.size)
        // La palabra trae 0.2 s de antes, para no cortar el principio.
        assertTrue(words[0].size >= 16_000 * 7 / 10)
    }

    @Test
    fun ignoresLongTalkAndClicks() {
        assertEquals(0, feedAll(WakeSegmenter(), listOf(100 to 500, 5_000 to 2_500, 100 to 500)).size)
        assertEquals(0, feedAll(WakeSegmenter(), listOf(100 to 500, 5_000 to 50, 100 to 500)).size)
    }

    @Test
    fun aWordAfterAPauseCountsEvenAfterTalking() {
        val words = feedAll(WakeSegmenter(), listOf(100 to 500, 5_000 to 2_500, 100 to 500, 5_000 to 400, 100 to 500))
        assertEquals(1, words.size)
    }
}
