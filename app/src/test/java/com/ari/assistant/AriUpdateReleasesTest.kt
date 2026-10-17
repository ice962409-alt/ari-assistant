package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cómo elige ARI su versión en la lista de Releases de GitHub (necesita el org.json de Android,
 * por eso corre con Robolectric). Las notas imitan las que escribe android.yml.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AriUpdateReleasesTest {

    private val branch = "ccr-e45f2eee-wkjpdl"

    private fun release(
        tag: String,
        run: Long,
        number: Int = 127,
        fromBranch: String = branch,
        draft: Boolean = false,
        asset: String = "ari.apk",
        state: String = "uploaded"
    ) = """
        {"tag_name": "$tag", "name": "ARI ${tag.removePrefix("ari-v")} (del dueño)", "draft": $draft,
         "published_at": "2026-10-16T13:52:26Z",
         "body": "ARI del dueño ${tag.removePrefix("ari-v")}. Compilación $number (run $run), rama $fromBranch, commit 62426d1848be6e6475ba8bfef0292107bfded867. Repositorio privado: no compartas este APK.",
         "assets": [
           {"id": 7001, "name": "ari-mapa.zip", "size": 4833643, "state": "uploaded"},
           {"id": 7002, "name": "$asset", "size": 82543916, "state": "$state"}
         ]}
    """.trimIndent()

    @Test
    fun readsTheReleaseThatCiPublishes() {
        val found = AriUpdate.newestRelease("[${release("ari-v0.66.1", 38909849572)}]")!!
        assertEquals(38909849572L, found.runId)
        assertEquals(127, found.number)
        assertEquals("ARI 0.66.1 (del dueño)", found.title)
        assertEquals("2026-10-16", found.created)
        assertEquals(7002L, found.assetId)
        assertEquals(82543916L, found.size)
    }

    @Test
    fun choosesTheHighestVersionOfAnyBranch() {
        val json = "[" + listOf(
            release("ari-v0.66.1", 38909849572),
            release("ari-v0.67.0", 38950000000, number = 130),
            // Otra rama (cada sesión de Claude usa una nueva): también cuenta.
            release("ari-v0.68.0", 39000000000, number = 131, fromBranch = "ccr-1b43ba33-9f7lpu"),
            release("ari-v0.69.0", 39100000000, draft = true),
            release("ari-v0.70.0", 39200000000, asset = "otra.apk"),
            release("ari-v0.71.0", 39300000000, state = "starter"),
            release("v0.72.0", 39400000000)
        ).joinToString(",") + "]"
        val found = AriUpdate.newestRelease(json)!!
        assertEquals(39000000000L, found.runId)
        assertEquals(131, found.number)
        assertEquals("0.68.0", found.version)
    }

    @Test
    fun anOldVersionBuiltLaterDoesNotWin() {
        val json = "[" + listOf(
            release("ari-v0.68.0", 39000000000),
            release("ari-v0.67.1", 39500000000, fromBranch = "main")
        ).joinToString(",") + "]"
        assertEquals("0.68.0", AriUpdate.newestRelease(json)!!.version)
    }

    @Test
    fun nothingUsefulMeansNull() {
        assertNull(AriUpdate.newestRelease("[]"))
        assertNull(AriUpdate.newestRelease("{\"message\": \"Not Found\"}"))
        assertNull(AriUpdate.newestRelease("no es json"))
        assertNull(AriUpdate.newestRelease("[${release("ari-v0.66.1", 38909849572, draft = true)}]"))
    }

    @Test
    fun comparesVersionsNumberByNumber() {
        assertTrue(AriUpdate.isNewerRelease("0.69.0", 1, "0.68.0", 5))
        assertTrue(AriUpdate.isNewerRelease("0.68.0", 6, "0.68.0", 5))
        assertFalse(AriUpdate.isNewerRelease("0.68.0", 5, "0.68.0", 5))
        assertFalse(AriUpdate.isNewerRelease("0.67.9", 9, "0.68.0", 5))
        assertTrue(AriUpdate.compareVersions("0.10.0", "0.9.9") > 0)
        assertEquals(0, AriUpdate.compareVersions("0.69.0", "0.69.0-otros"))
    }
}
