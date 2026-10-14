package com.ari.assistant.voz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BargeInTest {

    private fun chunk(level: Short) = ShortArray(800) { if (it % 2 == 0) level else (-level).toShort() }

    @Test
    fun laVozDeAriSolaNoInterrumpe() {
        val detector = BargeInDetector()
        repeat(100) { assertFalse(detector.feed(chunk(1200))) }
    }

    @Test
    fun tuVozFuerteSiInterrumpe() {
        val detector = BargeInDetector()
        repeat(12) { detector.feed(chunk(1200)) }
        var triggered = false
        repeat(10) { triggered = detector.feed(chunk(9000)) || triggered }
        assertTrue(triggered)
    }

    @Test
    fun unGolpeCortoNoCuenta() {
        val detector = BargeInDetector()
        repeat(12) { detector.feed(chunk(1200)) }
        assertFalse(detector.feed(chunk(9000)))
        repeat(20) { assertFalse(detector.feed(chunk(1200))) }
    }
}
