package com.ari.assistant.voz

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda las huellas de voz cifradas. La clave vive en el Keystore (la caja fuerte del
 * teléfono) y no se puede sacar: aunque alguien copiara los archivos de Ari, no podría
 * leerlas. Nunca se guarda una grabación, solo los números de cada huella.
 *
 * A propósito no van en el respaldo cifrado: son datos biométricos y, en un teléfono
 * nuevo, cada quien vuelve a registrar su voz (toma medio minuto).
 */
class VoiceVault(private val context: Context) {

    private val file get() = File(context.filesDir, FILE_NAME)

    fun load(): List<VoicePrint> {
        if (!file.exists()) return emptyList()
        return try {
            val sealed = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            val json = JSONArray(String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8))
            (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)
                val samples = item.getJSONArray("samples")
                VoicePrint(
                    name = item.getString("name"),
                    owner = item.optBoolean("owner"),
                    samples = (0 until samples.length()).map {
                        VoicePrints.fromBytes(Base64.decode(samples.getString(it), Base64.NO_WRAP))
                    }
                )
            }
        } catch (_: Exception) {
            // Si la caja fuerte cambió (por ejemplo, al restaurar el teléfono), no se puede leer:
            // es como no tener voces registradas.
            emptyList()
        }
    }

    fun save(prints: List<VoicePrint>) {
        if (prints.isEmpty()) {
            file.delete()
            return
        }
        val json = JSONArray()
        prints.forEach { print ->
            json.put(JSONObject().apply {
                put("name", print.name)
                put("owner", print.owner)
                put("samples", JSONArray().apply {
                    print.samples.forEach { put(Base64.encodeToString(VoicePrints.toBytes(it), Base64.NO_WRAP)) }
                })
            })
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeBytes(sealed)
        temp.renameTo(file)
    }

    fun clear() {
        file.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val FILE_NAME = "voces.bin"
        private const val KEY_ALIAS = "ari_voces"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
