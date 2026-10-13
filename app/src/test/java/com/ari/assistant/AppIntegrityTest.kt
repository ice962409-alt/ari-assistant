package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.KeyStore

class AppIntegrityTest {

    /** La huella guardada en la app es la de la clave con la que se firman los APK (si no, Ari no abriría). */
    @Test
    fun laHuellaEsLaDeLaClaveDeFirma() {
        val file = listOf(File("debug.keystore"), File("app/debug.keystore")).first { it.isFile }
        val store = KeyStore.getInstance("PKCS12")
        file.inputStream().use { store.load(it, "android".toCharArray()) }
        val certificate = store.getCertificate("androiddebugkey")
        assertEquals(AppIntegrity.OFFICIAL_CERT_SHA256, AppIntegrity.fingerprint(certificate.encoded))
    }
}
