package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceStyleTest {

    @Test
    fun keepsValuesInaSensibleRange() {
        assertEquals(0.5f, VoiceStyle.clampRate(0.1f), 0.001f)
        assertEquals(2.0f, VoiceStyle.clampRate(5f), 0.001f)
        assertEquals(1.25f, VoiceStyle.clampRate(1.25f), 0.001f)
        assertEquals(0.5f, VoiceStyle.clampPitch(0f), 0.001f)
        assertEquals(2.0f, VoiceStyle.clampPitch(3f), 0.001f)
    }

    @Test
    fun picksTheClosestLabel() {
        assertEquals("Normal", VoiceStyle.label(VoiceStyle.SPEEDS, 1.0f))
        assertEquals("Rápida", VoiceStyle.label(VoiceStyle.SPEEDS, 1.3f))
        assertEquals("Muy rápida", VoiceStyle.label(VoiceStyle.SPEEDS, 1.5f))
        assertEquals("Lenta", VoiceStyle.label(VoiceStyle.SPEEDS, 0.6f))
        assertEquals("Grave", VoiceStyle.label(VoiceStyle.PITCHES, 0.8f))
        assertEquals("Agudo", VoiceStyle.label(VoiceStyle.PITCHES, 1.4f))
        assertEquals("Normal", VoiceStyle.label(VoiceStyle.PITCHES, 1.0f))
    }

    @Test
    fun everyOptionIsWithinRange() {
        VoiceStyle.SPEEDS.forEach { (_, value) -> assertEquals(value, VoiceStyle.clampRate(value), 0.001f) }
        VoiceStyle.PITCHES.forEach { (_, value) -> assertEquals(value, VoiceStyle.clampPitch(value), 0.001f) }
    }

    @Test
    fun labelRoundTripsForEveryOption() {
        VoiceStyle.SPEEDS.forEach { (name, value) -> assertEquals(name, VoiceStyle.label(VoiceStyle.SPEEDS, value)) }
        VoiceStyle.PITCHES.forEach { (name, value) -> assertEquals(name, VoiceStyle.label(VoiceStyle.PITCHES, value)) }
    }
}
