package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada usuario tiene su Ari: su nombre y el tono que eligió llegan a la personalidad. */
class ProfileTest {

    @Test
    fun elCreadorEsReconocido() {
        val text = Profile("Javier", Tone.FRANK_SWEARING, creator = true).personality()
        assertTrue(text.contains("asistente personal de Javier, quien te creó con ayuda de Claude"))
        assertTrue(text.contains("empresa de Javier"))
        assertTrue(text.contains("grosería"))
        assertTrue(text.contains("Llama a Javier por su nombre"))
        assertTrue(text.contains("totalmente franca"))
        assertTrue(text.endsWith("Respondes siempre en español."))
    }

    @Test
    fun otroUsuarioNoEsElCreador() {
        val text = Profile("Ana", Tone.PROFESSIONAL).personality()
        assertTrue(text.contains("asistente personal de Ana."))
        assertFalse(text.contains("te creó"))
        assertTrue(text.contains("tono profesional"))
        assertFalse(text.contains("sueltas alguna grosería"))
    }

    @Test
    fun sinNombreNoInventaUno() {
        val text = Profile().personality()
        assertTrue(text.contains("asistente personal de tu usuario"))
        assertFalse(text.contains("por su nombre"))
        assertTrue(text.contains("No usas groserías"))
    }

    @Test
    fun laAriParaOtrosNoEsCeoYAyudaEnElDiaADia() {
        val text = Profile("Ana").personality(ceo = false)
        assertFalse(text.contains("CEO"))
        assertFalse(text.contains("totalmente franca"))
        assertTrue(text.contains("vida diaria"))
        assertTrue(text.contains("enséñame"))
        assertTrue(text.endsWith("Respondes siempre en español."))
    }

    @Test
    fun limpiaElNombre() {
        assertEquals("María José", Profile.cleanName("  María\n  José "))
        assertEquals(Profile.MAX_NAME_LENGTH, Profile.cleanName("a".repeat(100)).length)
    }

    @Test
    fun tonoGuardado() {
        assertEquals(Tone.PROFESSIONAL, Profile.toneNamed("PROFESSIONAL"))
        assertNull(Profile.toneNamed("NO_EXISTE"))
        assertNull(Profile.toneNamed(null))
    }

    @Test
    fun neverConfusesRolesOrInventsFeatures() {
        val text = Profile("Javier").personality()
        assertTrue(text.contains("nunca le llames Ari"))
        assertTrue(text.contains("nunca inventes menús"))
    }

    @Test
    fun traderIsACoachNotAnAdvisor() {
        val text = Profile("Carlos").personality(ceo = false, trader = true)
        assertTrue(text.contains("trading"))
        assertTrue(text.contains("ENTRENADORA") || text.contains("entrenadora"))
        assertTrue(text.contains("no das consejos financieros"))
        assertTrue(text.contains("perder dinero"))
        assertFalse(text.contains("CEO"))
    }

}
