package com.ari.assistant.voz

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sin

class VoiceTest {

    private fun tone(ms: Int, amplitude: Int): ShortArray =
        ShortArray(16 * ms) { (amplitude * sin(it * 0.3)).toInt().toShort() }

    private fun feedInChunks(detector: SpeechEndDetector, audio: ShortArray): SpeechEndDetector.State {
        var state = detector.state
        audio.toList().chunked(320).forEach { state = detector.feed(it.toShortArray()) }
        return state
    }

    @Test
    fun detectaQueTerminasteDeHablar() {
        val detector = SpeechEndDetector()
        feedInChunks(detector, tone(400, 50))
        assertEquals(SpeechEndDetector.State.WAITING, detector.state)
        feedInChunks(detector, tone(1200, 8000))
        assertEquals(SpeechEndDetector.State.SPEAKING, detector.state)
        assertEquals(SpeechEndDetector.State.ENDED, feedInChunks(detector, tone(1000, 50)))
    }

    @Test
    fun siNoHablasNoEsperaParaSiempre() {
        val detector = SpeechEndDetector(noSpeechMs = 2000)
        assertEquals(SpeechEndDetector.State.NO_SPEECH, feedInChunks(detector, tone(2500, 60)))
    }

    @Test
    fun unaPausaCortaNoCorta() {
        val detector = SpeechEndDetector()
        feedInChunks(detector, tone(300, 50))
        feedInChunks(detector, tone(800, 8000))
        feedInChunks(detector, tone(400, 50))
        assertEquals(SpeechEndDetector.State.SPEAKING, detector.state)
    }

    @Test
    fun reconoceALaPersonaMasParecida() {
        val yo = VoicePrint("Javier", owner = true, samples = listOf(floatArrayOf(1f, 0.1f, 0f), floatArrayOf(0.9f, 0.2f, 0f)))
        val mama = VoicePrint("Rosa", owner = false, samples = listOf(floatArrayOf(0f, 1f, 0.2f)))
        assertEquals("Javier", VoicePrints.identify(floatArrayOf(0.95f, 0.15f, 0.05f), listOf(yo, mama))?.name)
        assertEquals("Rosa", VoicePrints.identify(floatArrayOf(0.1f, 0.9f, 0.3f), listOf(yo, mama))?.name)
        assertNull(VoicePrints.identify(floatArrayOf(0f, 0f, 1f), listOf(yo, mama)))
    }

    @Test
    fun siDosEmpatanNoAdivina() {
        val a = VoicePrint("A", false, listOf(floatArrayOf(1f, 1f)))
        val b = VoicePrint("B", false, listOf(floatArrayOf(1f, 0.98f)))
        assertNull(VoicePrints.identify(floatArrayOf(1f, 0.99f), listOf(a, b)))
    }

    @Test
    fun guardaYLeeLosNumeros() {
        val values = floatArrayOf(0.5f, -1.25f, 3f)
        assertArrayEquals(values, VoicePrints.fromBytes(VoicePrints.toBytes(values)), 0f)
    }

    @Test
    fun deLejosSigueSiendoElDueno() {
        // Solo está registrada la voz del dueño; de lejos su frase se parece menos (0.55 a medias).
        val yo = VoicePrint("Javier", owner = true, samples = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0.9f, 0.1f, 0f)))
        val farAway = floatArrayOf(0.45f, 0.89f, 0f)
        assertEquals("Javier", VoicePrints.identify(farAway, listOf(yo), ownerBefore = true)?.name)
        assertEquals("Javier", VoicePrints.identify(farAway, listOf(yo), ownerBefore = null)?.name)
        // Si venía hablando otra persona, o hay más voces registradas, no se relaja.
        assertNull(VoicePrints.identify(farAway, listOf(yo), ownerBefore = false))
        val rosa = VoicePrint("Rosa", owner = false, samples = listOf(floatArrayOf(0f, 0f, 1f)))
        assertNull(VoicePrints.identify(farAway, listOf(yo, rosa), ownerBefore = true))
        // Una voz muy distinta sigue siendo "voz nueva".
        assertNull(VoicePrints.identify(floatArrayOf(0f, 1f, 0f), listOf(yo), ownerBefore = true))
    }
}
