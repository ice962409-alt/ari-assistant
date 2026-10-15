package com.ari.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriTraderTest {

    @Test
    fun introTeachesAndWarns() {
        val intro = AriTrader.intro("Carlos")
        assertTrue(intro.contains("Carlos"))
        assertTrue(intro.contains("entrenadora"))
        assertTrue(intro.contains("no doy consejos financieros"))
        assertTrue(intro.contains("enséñame"))
        assertTrue(intro.contains("quiero aprender trading"))
    }

    @Test
    fun modelTipMentionsTheComputer() {
        assertTrue(AriTrader.MODEL_TIP.contains("computadora"))
        assertTrue(AriTrader.MODEL_TIP.contains("Importar modelo"))
    }

    @Test
    fun recognizesModelQuestions() {
        assertTrue(AriTrader.asksModel("cómo mejoro el modelo"))
        assertTrue(AriTrader.asksModel("quiero un modelo más listo"))
        assertTrue(AriTrader.asksModel("pon un mejor modelo"))
        assertFalse(AriTrader.asksModel("cómo va el mercado"))
    }
}
