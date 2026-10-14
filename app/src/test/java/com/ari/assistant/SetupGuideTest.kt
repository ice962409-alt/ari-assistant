package com.ari.assistant

import com.ari.assistant.SetupGuide.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideTest {

    @Test
    fun marksWhatIsDone() {
        val lines = SetupGuide.lines(setOf(Step.NAME))
        assertEquals("✓ Tu nombre", lines.first())
        assertTrue(lines[1].startsWith("○ Su cerebro"))
    }

    @Test
    fun tellsHowFarYouAre() {
        assertTrue(SetupGuide.progress(emptySet()).startsWith("Llevas 0"))
        val essentials = Step.values().filter { it.essential }.toSet()
        assertTrue(SetupGuide.essentialsDone(essentials))
        assertTrue(SetupGuide.progress(essentials).startsWith("Lo esencial está listo"))
        assertTrue(SetupGuide.progress(Step.values().toSet()).startsWith("¡Todo listo!"))
        assertFalse(SetupGuide.essentialsDone(setOf(Step.NAME)))
    }

    @Test
    fun understandsWhenYouAskForIt() {
        assertTrue(SetupGuide.asks("Configúrame"))
        assertTrue(SetupGuide.asks("configurar Ari"))
        assertFalse(SetupGuide.asks("¿qué tengo hoy?"))
    }
}
