package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriUpdateTest {

    @Test
    fun understandsTheRequest() {
        assertTrue(AriUpdate.asks("Busca actualizaciones"))
        assertTrue(AriUpdate.asks("ARI, actualízate"))
        assertTrue(AriUpdate.asks("¿Hay versión nueva?"))
        assertTrue(AriUpdate.asks("buscar actualización"))
        assertFalse(AriUpdate.asks("actualiza mi agenda"))
        assertFalse(AriUpdate.asks("busca en internet qué es una actualización"))
    }

    @Test
    fun newerMeansALaterRun() {
        assertTrue(AriUpdate.isNewer(38624808318, 38621699517))
        assertFalse(AriUpdate.isNewer(38621699517, 38621699517))
        assertFalse(AriUpdate.isNewer(38561077737, 38621699517))
        // Una versión compilada fuera de GitHub siempre puede actualizarse.
        assertTrue(AriUpdate.isNewer(1, 0))
    }

    @Test
    fun findsTheApkInTheZip() {
        assertEquals("app-completa-release.apk", AriUpdate.apkEntry(listOf("output-metadata.json", "app-completa-release.apk")))
        assertEquals("a/b.apk", AriUpdate.apkEntry(listOf("a/b.apk")))
        assertNull(AriUpdate.apkEntry(listOf("notas.txt")))
    }

    @Test
    fun explainsGitHubAnswers() {
        assertTrue(AriUpdate.explain(403).contains("Actions"))
        assertTrue(AriUpdate.explain(404).contains("Contents"))
        assertTrue(AriUpdate.explain(401).contains("llave"))
        assertTrue(AriUpdate.explain(-1).contains("internet"))
    }
}
