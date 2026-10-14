package com.ari.assistant

import com.ari.assistant.agents.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleEditorTest {

    @Test
    fun readsBirthdaysWrittenManyWays() {
        assertEquals("05-05", PeopleEditor.parseBirthday("5 de mayo"))
        assertEquals("05-05", PeopleEditor.parseBirthday(" 5 Mayo "))
        assertEquals("12-03", PeopleEditor.parseBirthday("3/12"))
        assertEquals("02-29", PeopleEditor.parseBirthday("29-02"))
        assertNull(PeopleEditor.parseBirthday("31 de febrero"))
        assertNull(PeopleEditor.parseBirthday("31/4"))
        assertNull(PeopleEditor.parseBirthday("mañana"))
        assertNull(PeopleEditor.parseBirthday(""))
    }

    @Test
    fun oneFactPerLine() {
        assertEquals(
            listOf("Es dentista", "Le gusta el café"),
            PeopleEditor.parseFacts("Es dentista\n\n• Le gusta el café\n- es dentista\n")
        )
        assertEquals(PeopleEditor.MAX_FACTS, PeopleEditor.parseFacts((1..15).joinToString("\n") { "dato $it" }).size)
    }

    @Test
    fun buildsTheCard() {
        val base = Person(7, "Luis", "primo", listOf("es dentista"), "05-05")
        val result = PeopleEditor.build(base, " luis  ángel ", "Tia", "", "vive en Puebla")
        assertTrue(result is PeopleEditor.Result.Ok)
        val person = (result as PeopleEditor.Result.Ok).person
        assertEquals(7L, person.id)
        assertEquals("Luis ángel", person.name)
        assertEquals("tía", person.relation)
        assertEquals(listOf("vive en Puebla"), person.facts)
        assertNull(person.birthday)
    }

    @Test
    fun nameFromRelationWhenEmpty() {
        val person = (PeopleEditor.build(Person(0, ""), "", "mamá", "3 de marzo", "") as PeopleEditor.Result.Ok).person
        assertEquals("Mamá", person.name)
        assertEquals("03-03", person.birthday)
    }

    @Test
    fun explainsWhatIsMissing() {
        assertTrue(PeopleEditor.build(Person(0, ""), " ", "", "", "") is PeopleEditor.Result.Error)
        assertTrue(PeopleEditor.build(Person(0, ""), "Ana", "", "el día de mi santo", "") is PeopleEditor.Result.Error)
    }

    @Test
    fun showsTheSavedBirthday() {
        assertEquals("5 de mayo", PeopleEditor.birthdayText(Person(1, "Luis", birthday = "05-05")))
        assertEquals("", PeopleEditor.birthdayText(Person(1, "Luis")))
    }
}
