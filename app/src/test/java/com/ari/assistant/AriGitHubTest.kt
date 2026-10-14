package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AriGitHubTest {

    @Test
    fun cleansWhatWasPasted() {
        assertEquals("github_pat_ABC123", AriGitHub.cleanToken("  \"github_pat_ABC\n123\" "))
        assertTrue(AriGitHub.looksLikeToken("github_pat_" + "x".repeat(40)))
        assertFalse(AriGitHub.looksLikeToken("ghp_corta"))
    }

    @Test
    fun explainsGitHubAnswers() {
        assertTrue(AriGitHub.explain(404).contains("ari-assistant"))
        assertTrue(AriGitHub.explain(403).contains("Issues"))
        assertTrue(AriGitHub.explain(401).contains("venció"))
        assertEquals("[ARI] Mejoras propuestas (0.53.0, 14/10/2026)", AriGitHub.title("0.53.0", "14/10/2026"))
    }
}
