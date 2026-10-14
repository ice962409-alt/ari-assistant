package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReportTest {

    @Test
    fun kindFromWhatHappened() {
        assertEquals(AutoReport.Kind.CIERRE, AutoReport.kind("Ari se cerró por un error del motor (por ejemplo, la GPU)"))
        assertEquals(AutoReport.Kind.CIERRE, AutoReport.kind("Ari se cerró por un error (hilo main)"))
        assertEquals(AutoReport.Kind.CONGELADA, AutoReport.kind("Ari se congeló y Android la cerró"))
        assertEquals(AutoReport.Kind.MEMORIA, AutoReport.kind("Android cerró a Ari por falta de memoria"))
        assertEquals(AutoReport.Kind.ERROR, AutoReport.kind("Fallo de Ari"))
    }

    @Test
    fun summaryIsTheFirstError() {
        val trace = "java.lang.IllegalStateException: Status Code: 13\n  at com.ari.Foo.bar(Foo.kt:3)\n"
        assertEquals("java.lang.IllegalStateException: Status Code: 13", AutoReport.summary("Ari se cerró", trace))
        val exit = "Fecha: Tue Oct 13\nAndroid dice: crash\nMemoria usada: 2648 MB"
        assertEquals("crash", AutoReport.summary("Ari se cerró por un error del motor", exit))
        assertEquals("Ari se congeló", AutoReport.summary("Ari se congeló", ""))
        assertTrue(AutoReport.summary("x", "e".repeat(500)).length <= 200)
    }

    @Test
    fun removesPersonalData() {
        val dirty = "No pude con ana.lopez@gmail.com, tel +52 55 1234 5678, https://example.com/x?a=1 y github_pat_ABCDEFGH12345"
        val clean = AutoReport.clean(dirty)
        assertFalse(clean.contains("ana.lopez"))
        assertFalse(clean.contains("1234 5678"))
        assertFalse(clean.contains("example.com"))
        assertFalse(clean.contains("github_pat_"))
        assertTrue(clean.contains("[correo]"))
        // Los números de línea del código se quedan.
        assertEquals("at Foo.kt:123", AutoReport.clean("at Foo.kt:123"))
    }

    @Test
    fun versionOnlyHasAllowedCharacters() {
        assertEquals("0.57.0-otros", AutoReport.version("0.57.0-otros"))
        assertEquals("0.57.0", AutoReport.version("0.57.0 (beta)").replace("beta", ""))
        assertEquals("0", AutoReport.version(""))
    }

    @Test
    fun bodyIsValidJson() {
        val body = AutoReport.body(linkedMapOf("platform" to "android", "summary" to "dijo \"hola\"\nadiós", "model" to ""))
        assertEquals("{\"platform\":\"android\",\"summary\":\"dijo \\\"hola\\\"\\nadiós\"}", body)
    }

    @Test
    fun bodyFitsIn32Kb() {
        val body = AutoReport.body(mapOf("platform" to "android", "details" to "x".repeat(40_000)))
        assertTrue(body.toByteArray().size <= 32 * 1024)
    }

    @Test
    fun retriesOnlyWhatCanSucceedLater() {
        assertTrue(AutoReport.done(201))
        assertTrue(AutoReport.done(200))
        assertTrue(AutoReport.done(400))
        assertTrue(AutoReport.done(413))
        assertFalse(AutoReport.done(429))
        assertFalse(AutoReport.done(503))
        assertFalse(AutoReport.done(-1))
        assertEquals(60_000L, AutoReport.backoffMillis(0))
        assertEquals(600_000L, AutoReport.backoffMillis(1))
        assertEquals(3_600_000L, AutoReport.backoffMillis(5))
    }

    @Test
    fun splitsASavedReport() {
        val report = FailureReport.text(listOf("Versión de Ari" to "0.56.0"), "Ari se congeló y Android la cerró", "main thread blocked")
        assertEquals("Ari se congeló y Android la cerró" to "main thread blocked", FailureReport.parts(report))
    }
}
