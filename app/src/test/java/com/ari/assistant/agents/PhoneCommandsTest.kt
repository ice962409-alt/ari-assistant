package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCommandsTest {

    private fun parse(text: String) = PhoneCommands.parse(text)

    @Test
    fun abrirApps() {
        assertEquals(PhoneAction.OpenApp("WhatsApp"), parse("Abre WhatsApp"))
        assertEquals(PhoneAction.OpenApp("Google Maps"), parse("Ari, por favor abre la app de Google Maps"))
        assertEquals(PhoneAction.OpenApp("Spotify"), parse("¿Puedes abrir Spotify?"))
        assertEquals(PhoneAction.Camera, parse("abre la cámara"))
        assertEquals(PhoneAction.Camera, parse("toma una foto"))
        assertEquals(PhoneAction.Settings("wifi"), parse("abre los ajustes de wifi"))
        assertEquals(PhoneAction.Settings("general"), parse("abre la configuración"))
    }

    @Test
    fun llamadas() {
        assertEquals(PhoneAction.Dial("5512345678"), parse("llama al 55 1234 5678"))
        assertEquals(PhoneAction.Dial("+525512345678"), parse("márcale al +52 55 1234 5678"))
        assertTrue(parse("llama a mamá") is PhoneAction.Missing)
        // Conversación, no una llamada.
        assertNull(parse("llama la atención que nadie vino a la fiesta"))
    }

    @Test
    fun whatsapp() {
        assertEquals(
            PhoneAction.WhatsApp("5512345678", "Ya voy, llego en 10"),
            parse("Manda un WhatsApp al 55 1234 5678 que diga Ya voy, llego en 10")
        )
        assertEquals(PhoneAction.WhatsApp("", "Feliz cumpleaños"), parse("envía un mensaje por WhatsApp que diga Feliz cumpleaños"))
        assertTrue(parse("manda un whatsapp") is PhoneAction.Missing)
    }

    @Test
    fun alarmasYTemporizadores() {
        assertEquals(PhoneAction.Alarm(7, 0, ""), parse("pon una alarma a las 7"))
        // La etiqueta es lo que dijiste después de la hora.
        assertEquals(PhoneAction.Alarm(18, 30, "el gym"), parse("pon una alarma a las 6:30 de la tarde para el gym"))
        assertEquals(PhoneAction.Alarm(6, 15, ""), parse("despiértame mañana a las 6 y cuarto"))
        assertEquals(PhoneAction.Timer(600, ""), parse("pon un temporizador de 10 minutos"))
        assertEquals(PhoneAction.Timer(1800, ""), parse("pon un temporizador de media hora"))
        assertTrue(parse("pon una alarma a las 30") is PhoneAction.Missing)
    }

    @Test
    fun busquedasYMapas() {
        assertEquals(PhoneAction.WebSearch("recetas de pozole"), parse("busca en Google recetas de pozole"))
        assertEquals(PhoneAction.Navigate("aeropuerto"), parse("¿Cómo llego al aeropuerto?"))
        assertEquals(PhoneAction.Navigate("casa de mi abuela"), parse("llévame a casa de mi abuela"))
    }

    @Test
    fun noSeMeteEnLaConversacion() {
        listOf("hola ari", "busca modelos", "recuérdame mañana a las 9 algo", "qué hora es", "recuerda que me gusta el café")
            .forEach { assertNull(it, parse(it)) }
    }

    @Test
    fun eligeLaApp() {
        val apps = listOf(
            InstalledApp("WhatsApp", "com.whatsapp"),
            InstalledApp("WhatsApp Business", "com.whatsapp.w4b"),
            InstalledApp("Spotify: música y podcasts", "com.spotify.music"),
            InstalledApp("Maps", "com.google.android.apps.maps")
        )
        assertEquals("com.whatsapp", (PhoneCommands.pickApp("whatsapp", apps) as PhoneCommands.AppChoice.Found).app.packageName)
        assertEquals("com.spotify.music", (PhoneCommands.pickApp("Spotify", apps) as PhoneCommands.AppChoice.Found).app.packageName)
        assertEquals("com.whatsapp.w4b", (PhoneCommands.pickApp("whatsapp business", apps) as PhoneCommands.AppChoice.Found).app.packageName)
        assertTrue(PhoneCommands.pickApp("whats", apps) is PhoneCommands.AppChoice.Several)
        assertTrue(PhoneCommands.pickApp("instagram", apps) is PhoneCommands.AppChoice.None)
        assertFalse(PhoneCommands.pickApp("", apps) is PhoneCommands.AppChoice.Found)
    }
}
