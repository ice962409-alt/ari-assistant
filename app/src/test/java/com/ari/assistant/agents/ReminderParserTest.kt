package com.ari.assistant.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Las frases de recordatorio que Ari debe entender. Hoy es martes 13 de octubre de 2026, 10:00. */
class ReminderParserTest {

    private val now = LocalDateTime.of(2026, 10, 13, 10, 0)

    private fun ok(message: String): ReminderParser.Result.Ok {
        val result = ReminderParser.parse(message, now)
        assertTrue("\"$message\" debería entenderse: $result", result is ReminderParser.Result.Ok)
        return result as ReminderParser.Result.Ok
    }

    private fun problem(message: String) {
        assertTrue("\"$message\" no debería programarse", ReminderParser.parse(message, now) is ReminderParser.Result.Problem)
    }

    @Test
    fun mananaALasNueve() {
        val r = ok("Recuérdame mañana a las 9 llamar a mamá")
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), r.dueAt)
        assertEquals("llamar a mamá", r.text)
    }

    @Test
    fun textoAntesDeLaHora() {
        val r = ok("recuérdame llamar a mamá mañana a las 9")
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), r.dueAt)
        assertEquals("llamar a mamá", r.text)
    }

    @Test
    fun enMinutosYHoras() {
        assertEquals(now.plusMinutes(20), ok("recuérdame en 20 minutos sacar la ropa").dueAt)
        assertEquals(now.plusHours(2), ok("avísame en 2 horas que revise el horno").dueAt)
        assertEquals(now.plusMinutes(30), ok("recuérdame en media hora tomar agua").dueAt)
        assertEquals("revise el horno", ok("avísame en 2 horas que revise el horno").text)
    }

    @Test
    fun deLaTardeNoEsManana() {
        val r = ok("recuérdame a las 5 de la tarde ir al gym")
        assertEquals(LocalDateTime.of(2026, 10, 13, 17, 0), r.dueAt)
        assertEquals("ir al gym", r.text)
        // "de la mañana" es la hora, no el día de mañana.
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), ok("recuérdame a las 9 de la mañana ir al banco").dueAt)
    }

    @Test
    fun horaSinTardeNiManana() {
        // A las 10:00, "a las 3" es hoy a las 15:00.
        assertEquals(LocalDateTime.of(2026, 10, 13, 15, 0), ok("recuérdame a las 3 pagar la luz").dueAt)
        // "mañana a las 3" también es en la tarde.
        assertEquals(LocalDateTime.of(2026, 10, 14, 15, 0), ok("recuérdame mañana a las 3 pagar la luz").dueAt)
        assertEquals(LocalDateTime.of(2026, 10, 13, 18, 30), ok("recuérdame a las 6:30 pm salir").dueAt)
        assertEquals(LocalDateTime.of(2026, 10, 13, 16, 30), ok("recuérdame a las 4 y media la junta").dueAt)
    }

    @Test
    fun diaDeLaSemana() {
        val r = ok("recuérdame el viernes a las 10 entregar el reporte")
        assertEquals(LocalDateTime.of(2026, 10, 16, 10, 0), r.dueAt)
        assertEquals("entregar el reporte", r.text)
        // Hoy es martes: "el martes" es la próxima semana.
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), ok("recuérdame el martes la cita").dueAt)
    }

    @Test
    fun queAlPrincipioSeQuita() {
        assertEquals("tengo junta", ok("recuérdame que mañana a las 4 tengo junta").text)
    }

    @Test
    fun problemasSeExplican() {
        problem("recuérdame llamar a mamá")          // falta cuándo
        problem("recuérdame mañana a las 9")         // falta qué
        problem("recuérdame hoy a las 8 de la mañana desayunar") // ya pasó
        problem("recuérdame a las 27 algo")
    }

    @Test
    fun reconoceLaPeticion() {
        assertTrue(ReminderParser.isRequest(ReminderParser.plain("Recuérdame mañana algo")))
        assertTrue(ReminderParser.isRequest(ReminderParser.plain("Ari, avísame en 5 minutos")))
        assertFalse(ReminderParser.isRequest(ReminderParser.plain("recuerda que me gusta el café")))
    }
}
