package com.ari.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriAbilitiesTest {

    @Test
    fun reconocePreguntas() {
        listOf("¿Qué puedes hacer?", "Ari, ¿qué sabes hacer?", "ayuda", "¿En qué me puedes ayudar?", "¿Para qué sirves?", "y qué más puedes hacer")
            .forEach { assertTrue(it, AriAbilities.asks(it)) }
    }

    @Test
    fun noConfundeOtrasPreguntas() {
        listOf("¿qué puedes hacer con este Excel?", "ayúdame a escribir un correo", "qué hora es")
            .forEach { assertFalse(it, AriAbilities.asks(it)) }
    }

    @Test
    fun soloOfreceLoQueElTelefonoPuede() {
        val basic = AriAbilities.intro("Ana", AriAbilities.Phone(vision = false, voices = false))
        assertTrue(basic.contains("Ana"))
        assertFalse(basic.contains("mira mi pantalla"))
        assertFalse(basic.contains("Reconocer voces"))
        val full = AriAbilities.intro("", AriAbilities.Phone(vision = true, voices = true))
        assertTrue(full.contains("mira mi pantalla"))
        assertTrue(full.contains("Reconocer voces"))
        assertTrue(full.contains("Recordatorios"))
    }
}
