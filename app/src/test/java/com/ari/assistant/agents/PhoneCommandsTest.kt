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
        assertEquals(PhoneAction.OpenApp("WhatsApp"), parse("Accede a WhatsApp"))
        assertEquals(PhoneAction.OpenApp("Instagram"), parse("métete a Instagram"))
        assertEquals(PhoneAction.OpenApp("YouTube"), parse("entra a YouTube"))
        assertEquals(PhoneAction.Camera, parse("abre la cámara"))
        assertEquals(PhoneAction.Camera, parse("toma una foto"))
        assertEquals(PhoneAction.Settings("wifi"), parse("abre los ajustes de wifi"))
        assertEquals(PhoneAction.Settings("general"), parse("abre la configuración"))
    }

    @Test
    fun llamadas() {
        assertEquals(PhoneAction.Dial("5512345678"), parse("llama al 55 1234 5678"))
        assertEquals(PhoneAction.Dial("+525512345678"), parse("márcale al +52 55 1234 5678"))
        assertEquals(PhoneAction.DialContact("mamá"), parse("llama a mamá"))
        assertEquals(PhoneAction.DialContact("Juan Pérez"), parse("márcale a Juan Pérez"))
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
        assertEquals(PhoneAction.WhatsAppContact("Juan", "Ya voy"), parse("Manda un WhatsApp a Juan que diga Ya voy"))
        assertEquals(PhoneAction.WhatsAppContact("mi hermana", "llego a las 8"), parse("envíale un whatsapp a mi hermana diciendo llego a las 8"))
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

    @Test
    fun mensajesYCorreos() {
        // Un "mensaje" a secas va por WhatsApp; SMS solo si lo pides.
        assertEquals(PhoneAction.WhatsAppContact("mamá", "ya llegué"), parse("Mándale un mensaje a mamá que diga ya llegué"))
        assertEquals(PhoneAction.SmsContact("mamá", "ya llegué"), parse("Mándale un SMS a mamá que diga ya llegué"))
        assertEquals(PhoneAction.SmsContact("mamá", "hola"), parse("manda un mensaje de texto a mamá que diga hola"))
        assertEquals(PhoneAction.Sms("5512345678", "hola"), parse("manda un sms al 5512345678 que diga hola"))
        assertEquals(
            PhoneAction.Email("ana@ejemplo.com", "llego tarde"),
            parse("escríbele un correo a ana@ejemplo.com que diga llego tarde")
        )
        // Pedir que Ari redacte algo no es enviarlo: eso lo contesta Gemma.
        assertNull(parse("escribe un mensaje bonito para mi novia"))
        assertNull(parse("escribe un correo para mi jefe pidiendo vacaciones"))
    }

    @Test
    fun musicaYYoutube() {
        assertEquals(PhoneAction.PlayMusic("Bad Bunny", ""), parse("pon música de Bad Bunny"))
        assertEquals(PhoneAction.PlayMusic("Despacito", "spotify"), parse("pon Despacito en Spotify"))
        assertEquals(PhoneAction.PlayMusic("Bohemian Rhapsody", ""), parse("reproduce Bohemian Rhapsody"))
        assertEquals(PhoneAction.YouTube("Shape of You"), parse("pon la canción Shape of You en YouTube"))
        assertEquals(PhoneAction.YouTube("cómo hacer pan"), parse("busca en YouTube cómo hacer pan"))
    }

    @Test
    fun citasEnElCalendario() {
        val now = java.time.LocalDateTime.of(2026, 10, 13, 10, 0)
        assertEquals(
            PhoneAction.CalendarEvent("cita con el dentista", java.time.LocalDateTime.of(2026, 10, 16, 17, 0)),
            PhoneCommands.parse("agenda cita con el dentista el viernes a las 5", now)
        )
        assertEquals(
            PhoneAction.CalendarEvent("reunión con Pedro", java.time.LocalDateTime.of(2026, 10, 14, 10, 0)),
            PhoneCommands.parse("agenda una reunión mañana a las 10 con Pedro", now)
        )
        assertTrue(PhoneCommands.parse("agenda algo", now) is PhoneAction.Missing)
        // "anota que…" sigue siendo un recuerdo, no una cita.
        assertNull(PhoneCommands.parse("anota que mañana hay junta", now))
    }

    @Test
    fun eligeElContacto() {
        val contacts = listOf(
            PhoneContact("Mamá", "+525511111111"),
            PhoneContact("Mamá", "5522222222"),
            PhoneContact("Juan Pérez", "5533333333"),
            PhoneContact("Juan Carlos", "5544444444")
        )
        // Sin acentos también la encuentra, y con dos números gana el primero (el principal).
        val mama = PhoneCommands.pickContact("mama", contacts) as PhoneCommands.ContactChoice.Found
        assertEquals("+525511111111", mama.contact.number)
        assertTrue(PhoneCommands.pickContact("juan", contacts) is PhoneCommands.ContactChoice.Several)
        assertEquals("5533333333", (PhoneCommands.pickContact("juan perez", contacts) as PhoneCommands.ContactChoice.Found).contact.number)
        assertTrue(PhoneCommands.pickContact("Pedro", contacts) is PhoneCommands.ContactChoice.None)
    }

    @Test
    fun pedirViajeYNotas() {
        assertEquals(PhoneAction.Ride("aeropuerto", "uber"), parse("Pide un Uber al aeropuerto"))
        assertEquals(PhoneAction.Ride("Plaza Satélite", "didi"), parse("pídeme un DiDi a Plaza Satélite"))
        assertEquals(PhoneAction.Ride("", "uber"), parse("pide un taxi"))
        assertEquals(PhoneAction.Note("comprar leche"), parse("anota en Keep comprar leche"))
        assertEquals(PhoneAction.Note("llamar al dentista"), parse("Apunta en mis notas que llamar al dentista"))
    }

    @Test
    fun whatsappPorVozComoLoDices() {
        // El caso del informe del 17/10/2026.
        assertEquals(
            PhoneAction.WhatsAppContact("Fernanda", "Hola te saluda Ari, cómo estás"),
            parse("mándale un mensaje a Fernanda que diga Hola te saluda Ari, cómo estás?")
        )
        assertEquals(PhoneAction.WhatsAppContact("mi niña", "ya voy"), parse("dile a mi niña por WhatsApp que ya voy"))
        assertEquals(PhoneAction.WhatsAppContact("Juan", "ya voy"), parse("dile por whatsapp a Juan que ya voy"))
        assertEquals(PhoneAction.WhatsAppContact("Ana", "ya voy"), parse("mándale un whatsapp a Ana que ya voy"))
        assertEquals(PhoneAction.WhatsAppContact("mi niña", "ya voy"), parse("dile a mi niña que ya voy por WhatsApp"))
        assertEquals(PhoneAction.WhatsAppContact("Juan", "llego tarde"), parse("mándale un mensaje a Juan por guasap que le diga llego tarde"))
        assertEquals(PhoneAction.WhatsApp("5512345678", "hola"), parse("manda un mensaje al 55 1234 5678 por WhatsApp que diga hola"))
    }

    @Test
    fun sinTextoPreguntaQueLeEscribo() {
        assertEquals(PhoneAction.WhatsAppAsk("mi niña"), parse("mándale un mensaje a mi niña por WhatsApp"))
        assertEquals(PhoneAction.WhatsAppAsk("mi niña"), parse("Mándale un mensaje a mi niña"))
        assertEquals(PhoneAction.WhatsAppAsk("mi hermana"), parse("manda un guasap a mi hermana"))
        assertEquals(PhoneAction.WhatsAppAsk("", "5512345678"), parse("manda un whatsapp al 55 1234 5678"))
        assertTrue(parse("manda un whatsapp") is PhoneAction.Missing)
        // Pedir que lo redacte sigue siendo cosa de Gemma.
        assertNull(parse("escribe un mensaje para mi novia que sea bonito"))
        assertNull(parse("escribe un whatsapp bonito para mi novia"))
    }

    @Test
    fun miNinaEncuentraANina() {
        val contacts = listOf(PhoneContact("Niña", "5511111111"), PhoneContact("Mamá", "5522222222"))
        val found = PhoneCommands.pickContact("mi niña", contacts)
        assertEquals(PhoneContact("Niña", "5511111111"), (found as PhoneCommands.ContactChoice.Found).contact)
        // Si alguien se llama "Mi niña", gana ese.
        val exact = PhoneCommands.pickContact("mi niña", contacts + PhoneContact("Mi Niña ❤️", "5533333333"))
        assertEquals("5533333333", (exact as PhoneCommands.ContactChoice.Found).contact.number)
    }
}
