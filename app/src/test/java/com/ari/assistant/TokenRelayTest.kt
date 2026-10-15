package com.ari.assistant

import com.ari.assistant.agents.ReminderParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TokenRelayTest {

    @Test
    fun recognizesTheIntent() {
        assertTrue(TokenRelay.matches("los tokens se reactivan a las 3"))
        assertTrue(TokenRelay.matches("avísame cuando se reactiven tus tokens a las 9"))
        assertTrue(TokenRelay.matches("mañana ya puedo seguir con Claude a las 8"))
        assertFalse(TokenRelay.matches("ponme una alarma a las 7"))
        assertFalse(TokenRelay.matches("cómo va el proyecto"))
    }

    @Test
    fun buildsAReminderThatParses() {
        val now = LocalDateTime.of(2026, 10, 15, 10, 0)
        val msg = TokenRelay.toReminder("los tokens se reactivan a las 3 de la tarde")!!
        assertTrue(msg.startsWith("recuérdame "))
        assertTrue(msg.contains(TokenRelay.MESSAGE))
        val parsed = ReminderParser.parse(msg, now)
        assertTrue(parsed is ReminderParser.Result.Ok)
        val ok = parsed as ReminderParser.Result.Ok
        assertEquals15(ok.dueAt)
        assertTrue(ok.text.contains("sigue"))
    }

    private fun assertEquals15(dueAt: LocalDateTime) {
        org.junit.Assert.assertEquals(15, dueAt.hour)
    }

    @Test
    fun asksForTheTimeWhenMissing() {
        assertNull(TokenRelay.toReminder("los tokens ya se reactivaron"))
    }
}
