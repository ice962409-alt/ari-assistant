package com.ari.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRequestTest {

    @Test
    fun recognizesRequestsToLook() {
        listOf(
            "Mira mi pantalla",
            "Ari, ¿qué hay en mi pantalla?",
            "¿Qué ves en la pantalla?",
            "lee lo que dice mi pantalla",
            "¿puedes ver mi pantalla?",
            "Explícame lo que sale en pantalla",
            "checa la pantalla y dime qué opinas",
            "ayúdame con esto que tengo en la pantalla",
        ).forEach { assertTrue(it, ScreenRequest.matches(it)) }
    }

    @Test
    fun ignoresOtherScreenThings() {
        listOf(
            "Baja el brillo de la pantalla",
            "cambia el fondo de pantalla",
            "apaga la pantalla",
            "¿cuánto tiempo de pantalla llevo?",
            "graba la pantalla",
            "mira esta foto",
            "qué ves",
            "la pantalla de mi tele es grande",
        ).forEach { assertFalse(it, ScreenRequest.matches(it)) }
    }
}
