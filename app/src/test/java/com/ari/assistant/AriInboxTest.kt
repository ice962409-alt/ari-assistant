package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriInboxTest {

    private fun entry(key: String, app: String, title: String, text: String, at: Long, reply: Boolean = true) =
        AriInbox.Entry(key, app, title, text, at, reply)

    @Test
    fun understandsWhatYouAsk() {
        assertEquals(AriInbox.Request.Summary, AriInbox.parse("¿Qué notificaciones tengo?"))
        assertEquals(AriInbox.Request.Summary, AriInbox.parse("¿Quién me escribió?"))
        assertEquals(AriInbox.Request.Summary, AriInbox.parse("léeme mis notificaciones"))
        assertEquals(AriInbox.Request.Reply("juan", "Sí, ya voy"), AriInbox.parse("Respóndele a Juan que Sí, ya voy"))
        assertEquals(AriInbox.Request.Reply("mi mama", "llego a las 8"), AriInbox.parse("contéstale a mi mamá que llego a las 8"))
        assertNull(AriInbox.parse("dile a mi mamá que llego a las 8"))
        assertNull(AriInbox.parse("¿qué tengo hoy?"))
    }

    @Test
    fun summarizesByAppNewestFirst() {
        val text = AriInbox.summarize(
            listOf(
                entry("1", "WhatsApp", "Juan", "llego tarde", 1),
                entry("2", "WhatsApp", "Juan", "ya salí", 3),
                entry("3", "Gmail", "Banco", "tu estado de cuenta", 2, reply = false)
            )
        )
        assertEquals("WhatsApp — Juan (2): ya salí\nGmail — Banco: tu estado de cuenta", text)
        assertTrue(AriInbox.summarize(emptyList()).startsWith("No tienes"))
    }

    @Test
    fun findsWhoToAnswer() {
        val list = listOf(
            entry("1", "WhatsApp", "Juan Pérez", "hola", 1),
            entry("2", "WhatsApp", "Juan Pérez", "¿vienes?", 5),
            entry("3", "Gmail", "Juan", "factura", 9, reply = false),
            entry("4", "WhatsApp", "Mamá", "¿comiste?", 7)
        )
        assertEquals("2", AriInbox.findReplyTarget(list, "juan")?.key)
        assertEquals("4", AriInbox.findReplyTarget(list, "mama")?.key)
        assertNull(AriInbox.findReplyTarget(list, "pedro"))
    }

    @Test
    fun keepsOnlyRecentAndReplacesUpdates() {
        AriInbox.clear()
        AriInbox.add(entry("a", "WhatsApp", "Juan", "uno", 0))
        AriInbox.add(entry("a", "WhatsApp", "Juan", "dos", 10))
        AriInbox.add(entry("b", "Gmail", "Banco", "x", AriInbox.KEEP_MS + 5))
        val recent = AriInbox.recent(AriInbox.KEEP_MS + 6)
        assertEquals(listOf("dos", "x"), recent.map { it.text })
        AriInbox.clear()
    }
}
