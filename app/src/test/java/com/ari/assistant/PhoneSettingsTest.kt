package com.ari.assistant

import com.ari.assistant.PhoneSettings.Brightness
import com.ari.assistant.PhoneSettings.Command
import com.ari.assistant.PhoneSettings.Media
import com.ari.assistant.PhoneSettings.Volume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSettingsTest {

    @Test
    fun torchVolumeAndMusic() {
        assertEquals(Command.Torch(true), PhoneSettings.parse("Prende la linterna"))
        assertEquals(Command.Torch(false), PhoneSettings.parse("apaga la linterna"))
        assertEquals(Command.SetVolume(Volume.UP), PhoneSettings.parse("sube el volumen"))
        assertEquals(Command.SetVolume(Volume.DOWN), PhoneSettings.parse("Bájale al volumen"))
        assertEquals(Command.SetVolume(Volume.MAX), PhoneSettings.parse("volumen al máximo"))
        assertEquals(Command.SetVolume(Volume.MUTE), PhoneSettings.parse("pon el teléfono en silencio"))
        assertEquals(Command.SetVolume(Volume.UNMUTE), PhoneSettings.parse("quita el silencio"))
        assertEquals(Command.Music(Media.PAUSE), PhoneSettings.parse("pausa la música"))
        assertEquals(Command.Music(Media.NEXT), PhoneSettings.parse("siguiente canción"))
        assertEquals(Command.Music(Media.PREVIOUS), PhoneSettings.parse("canción anterior"))
    }

    @Test
    fun doNotDisturbAndBrightness() {
        assertEquals(Command.DoNotDisturb(true), PhoneSettings.parse("activa no molestar"))
        assertEquals(Command.DoNotDisturb(false), PhoneSettings.parse("quita el modo no molestar"))
        assertEquals(Command.SetBrightness(Brightness.UP), PhoneSettings.parse("sube el brillo"))
        assertEquals(Command.SetBrightness(Brightness.MIN), PhoneSettings.parse("brillo al mínimo"))
        assertEquals(255, PhoneSettings.nextBrightness(230, Brightness.UP))
        assertEquals(10, PhoneSettings.nextBrightness(40, Brightness.DOWN))
    }

    @Test
    fun ignoresTalkToAri() {
        // "cállate" o "siguiente" a secas son para Ari, no para el teléfono.
        assertNull(PhoneSettings.parse("cállate"))
        assertNull(PhoneSettings.parse("siguiente"))
        assertNull(PhoneSettings.parse("silencio"))
        assertNull(PhoneSettings.parse("¿qué tengo hoy?"))
    }

    @Test
    fun lockAndScreenshotAreAppControl() {
        assertEquals(AppControl.Command.Global(AppControl.GlobalAction.LOCK_SCREEN), AppControl.parse("bloquea la pantalla"))
        assertEquals(AppControl.Command.Global(AppControl.GlobalAction.SCREENSHOT), AppControl.parse("toma una captura de pantalla"))
    }

    @Test
    fun directCallChecksTheNumberOnScreen() {
        assertTrue(AutoCall.matches("Mamá +52 55 1234 5678 Llamar", "5512345678"))
        assertFalse(AutoCall.matches("Juan 55 9999 0000", "5512345678"))
        assertFalse(AutoCall.matches("123", "123"))
    }
}
