package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriRoutinesTest {

    private fun taught(text: String) = (AriRoutines.parse(text) as AriRoutines.Request.Teach).routine

    @Test
    fun learnsARoutineWithSteps() {
        val routine = taught("Ari, cuando te diga buenos días, dime qué tengo hoy y pon música")
        assertEquals("buenos dias", routine.trigger)
        assertEquals(listOf("dime que tengo hoy", "pon musica"), routine.steps)
    }

    @Test
    fun acceptsQuotesAndOtherWordings() {
        val routine = taught("cuando diga \"me voy\" abre maps, luego pon música de Juan y Juanes")
        assertEquals("me voy", routine.trigger)
        // "Juan y Juanes" no se parte: "Juanes" no es una orden.
        assertEquals(listOf("abre maps", "pon musica de juan y juanes"), routine.steps)
        val night = taught("Aprende que cuando te diga modo noche, apaga la burbuja y dime mi agenda de mañana")
        assertEquals("modo noche", night.trigger)
        assertEquals(2, night.steps.size)
    }

    @Test
    fun ignoresNormalTalk() {
        assertNull(AriRoutines.parse("cuando te diga que sí, hazlo"))
        assertNull(AriRoutines.parse("cuando te diga algo, escúchame bien porque es importante"))
        assertNull(AriRoutines.parse("¿qué tengo hoy?"))
        assertNull(AriRoutines.parse("cuando llegue a casa pon música"))
    }

    @Test
    fun listAndForget() {
        assertEquals(AriRoutines.Request.ListAll, AriRoutines.parse("¿Qué rutinas tienes?"))
        assertEquals(AriRoutines.Request.ListAll, AriRoutines.parse("mis rutinas"))
        assertEquals(AriRoutines.Request.Forget("buenos dias"), AriRoutines.parse("Olvida la rutina de buenos días"))
        assertEquals(AriRoutines.Request.Forget("modo noche"), AriRoutines.parse("borra la rutina \"modo noche\""))
    }

    @Test
    fun findsTheRoutineOnlyWithTheWholePhrase() {
        val routines = listOf(Routine("buenos dias", listOf("dime que tengo hoy")))
        assertEquals(routines[0], AriRoutines.find(routines, "¡Buenos días!"))
        assertEquals(routines[0], AriRoutines.find(routines, "Ari, buenos días"))
        assertNull(AriRoutines.find(routines, "buenos días, ¿cómo estás?"))
    }

    @Test
    fun addReplacesAndLimits() {
        var routines = AriRoutines.add(emptyList(), Routine("a", listOf("dime la hora")))
        routines = AriRoutines.add(routines, Routine("a", listOf("pon musica")))
        assertEquals(1, routines.size)
        assertEquals(listOf("pon musica"), routines[0].steps)
        repeat(40) { routines = AriRoutines.add(routines, Routine("r$it", listOf("dime la hora"))) }
        assertEquals(AriRoutines.MAX_ROUTINES, routines.size)
        assertTrue(routines.none { it.trigger == "a" })
        assertEquals(routines.size - 1, AriRoutines.remove(routines, "R39").size)
    }

    @Test
    fun savesAndReadsBack() {
        val routines = listOf(
            Routine("buenos dias", listOf("dime que tengo hoy", "pon musica")),
            Routine("me voy", listOf("abre maps"))
        )
        assertEquals(routines, AriRoutines.decode(AriRoutines.encode(routines)))
        assertEquals(emptyList<Routine>(), AriRoutines.decode(null))
        assertEquals(emptyList<Routine>(), AriRoutines.decode("solo disparador"))
    }

    @Test
    fun keepsAtMostSixSteps() {
        val routine = taught("cuando te diga todo, dime la hora, dime la fecha, pon musica, abre maps, abre whatsapp, dime mi agenda, dime un chiste")
        assertEquals(AriRoutines.MAX_STEPS, routine.steps.size)
    }
}
