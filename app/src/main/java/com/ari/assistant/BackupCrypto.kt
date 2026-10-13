package com.ari.assistant

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado del respaldo de Ari con contraseña.
 *
 * Formato: "ARI1" + sal (16 bytes) + IV (12 bytes) + datos cifrados con
 * AES-256-GCM. La llave sale de la contraseña con PBKDF2-SHA256, así que sin
 * la contraseña el archivo no se puede leer, y si alguien lo modifica, GCM lo
 * detecta y no se importa.
 */
object BackupCrypto {

    private val MAGIC = "ARI1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    /** La contraseña no sirvió o el archivo está dañado o modificado. */
    class WrongPasswordException : Exception("La contraseña no es correcta o el archivo está dañado.")

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val header = MAGIC.size + SALT_BYTES + IV_BYTES
        if (file.size <= header || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IllegalStateException("Este archivo no es un respaldo de Ari.")
        }
        val salt = file.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = file.copyOfRange(MAGIC.size + SALT_BYTES, header)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(file, header, file.size - header)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
