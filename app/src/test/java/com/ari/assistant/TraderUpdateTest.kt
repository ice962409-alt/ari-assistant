package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraderUpdateTest {

    @Test
    fun readsTheLatestFile() {
        val json = """{"versionCode":76,"versionName":"0.62.0","apkUrl":"https://example.com/AriTrader.apk","notes":"Diario de operaciones"}"""
        val latest = TraderUpdate.parse(json)!!
        assertEquals(76L, latest.code)
        assertEquals("0.62.0", latest.name)
        assertEquals("https://example.com/AriTrader.apk", latest.url)
        assertEquals("Diario de operaciones", latest.notes)
    }

    @Test
    fun rejectsInsecureOrBrokenFiles() {
        assertNull(TraderUpdate.parse("""{"apkUrl":"http://example.com/x.apk","versionCode":9}"""))
        assertNull(TraderUpdate.parse("no es json"))
        assertNull(TraderUpdate.parse("{}"))
    }

    @Test
    fun newerOnlyWhenCodeIsGreater() {
        assertTrue(TraderUpdate.isNewer(76, 75))
        assertFalse(TraderUpdate.isNewer(75, 75))
        assertFalse(TraderUpdate.isNewer(74, 75))
    }
}
