package com.ari.assistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** El respaldo cifrado: se abre con la contraseña correcta y con nada más. */
class BackupCryptoTest {

    private val content = "{\"recuerdos\":[\"me gusta el café\"]}".toByteArray()

    @Test
    fun seAbreConLaContrasenaCorrecta() {
        val file = BackupCrypto.encrypt(content, "contraseña-segura".toCharArray())
        assertFalse("El archivo no debe llevar el texto a la vista", String(file, Charsets.ISO_8859_1).contains("café"))
        assertArrayEquals(content, BackupCrypto.decrypt(file, "contraseña-segura".toCharArray()))
    }

    @Test
    fun contrasenaEquivocadaNoAbre() {
        val file = BackupCrypto.encrypt(content, "contraseña-segura".toCharArray())
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(file, "otra-contraseña".toCharArray())
        }
    }

    @Test
    fun archivoModificadoNoSeImporta() {
        val file = BackupCrypto.encrypt(content, "contraseña-segura".toCharArray())
        file[file.size - 5] = (file[file.size - 5].toInt() xor 1).toByte()
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(file, "contraseña-segura".toCharArray())
        }
    }

    @Test
    fun archivoQueNoEsDeAri() {
        assertThrows(IllegalStateException::class.java) {
            BackupCrypto.decrypt("hola, no soy un respaldo de Ari".toByteArray(), "contraseña-segura".toCharArray())
        }
    }

    @Test
    fun cadaRespaldoTieneSuPropiaSal() {
        val first = BackupCrypto.encrypt(content, "contraseña-segura".toCharArray())
        val second = BackupCrypto.encrypt(content, "contraseña-segura".toCharArray())
        assertFalse(first.contentEquals(second))
    }
}
