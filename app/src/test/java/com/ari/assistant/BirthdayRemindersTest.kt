package com.ari.assistant

import com.ari.assistant.agents.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BirthdayRemindersTest {

    private val luis = Person(1, "Luis", "primo", birthday = "05-05")
    private val rosa = Person(2, "Rosa", "mamá", birthday = "05-05")
    private val ana = Person(3, "Ana", birthday = "12-01")
    private val leap = Person(4, "Leo", birthday = "02-29")
    private val noDate = Person(5, "Beto")

    @Test
    fun findsWhoHasBirthdayTomorrow() {
        val people = listOf(luis, rosa, ana, noDate)
        assertEquals(listOf(luis, rosa), BirthdayReminders.tomorrow(people, LocalDate.of(2026, 5, 4)))
        assertTrue(BirthdayReminders.tomorrow(people, LocalDate.of(2026, 5, 5)).isEmpty())
        assertEquals(listOf(ana), BirthdayReminders.tomorrow(people, LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun leapDayIsRememberedOnThe28th() {
        assertEquals(listOf(leap), BirthdayReminders.tomorrow(listOf(leap), LocalDate.of(2027, 2, 27)))
        assertTrue(BirthdayReminders.tomorrow(listOf(leap), LocalDate.of(2027, 2, 28)).isEmpty())
        assertEquals(listOf(leap), BirthdayReminders.tomorrow(listOf(leap), LocalDate.of(2028, 2, 28)))
    }

    @Test
    fun writesTheMessage() {
        assertNull(BirthdayReminders.message(emptyList()))
        assertEquals("Mañana cumple años Luis (5 de mayo). ¿Le preparas algo?", BirthdayReminders.message(listOf(luis)))
        assertEquals("Mañana cumplen años Luis y Rosa (5 de mayo). ¿Les preparas algo?", BirthdayReminders.message(listOf(luis, rosa)))
    }

    @Test
    fun checksEveryDayAtNine() {
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), BirthdayReminders.nextCheck(LocalDateTime.of(2026, 10, 14, 7, 30)))
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 0), BirthdayReminders.nextCheck(LocalDateTime.of(2026, 10, 14, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 0), BirthdayReminders.nextCheck(LocalDateTime.of(2026, 10, 14, 22, 0)))
    }
}
