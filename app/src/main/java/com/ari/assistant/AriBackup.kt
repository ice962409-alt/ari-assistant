package com.ari.assistant

import android.content.SharedPreferences
import com.ari.assistant.agents.NetworkGuard
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Respaldo de Ari en un archivo cifrado con contraseña: recuerdos, historial,
 * bitácora del equipo, equipo contratado y redes aprobadas. Sirve para
 * guardar una copia y para mudar a Ari a otro teléfono o a la laptop.
 *
 * Formato: "ARI1" + sal (16 bytes) + IV (12 bytes) + JSON cifrado con
 * AES-256-GCM. La llave sale de la contraseña con PBKDF2-SHA256, así que sin
 * la contraseña el archivo no se puede leer, y si alguien lo modifica, GCM lo
 * detecta y no se importa.
 */
object AriBackup {

    const val MIN_PASSWORD_LENGTH = 8
    const val FILE_EXTENSION = "ari"

    private val MAGIC = "ARI1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024
    private const val FORMAT_VERSION = 1

    private const val KEY_APPROVED = "approved_networks"
    private const val TEAM_PREFIX = "team|"

    /** La contraseña no sirvió o el archivo está dañado o modificado. */
    class WrongPasswordException : Exception("La contraseña no es correcta o el archivo está dañado.")

    data class Summary(val memories: Int, val messages: Int, val teams: Int, val networks: Int)

    fun export(database: AriDatabase, settings: SharedPreferences, password: CharArray): Pair<ByteArray, Summary> {
        val data = database.exportData()
        val team = JSONObject()
        settings.all.forEach { (key, value) ->
            if (key.startsWith(TEAM_PREFIX) && value is String) team.put(key, value)
        }
        val networks = JSONArray(settings.getStringSet(KEY_APPROVED, emptySet()).orEmpty().sorted())
        val json = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("created_at", System.currentTimeMillis())
            .put("data", data)
            .put("team", team)
            .put("approved_networks", networks)
        val summary = Summary(
            data.getJSONArray("memories").length(),
            data.getJSONArray("messages").length(),
            team.length(),
            networks.length()
        )
        return encrypt(json.toString().toByteArray(Charsets.UTF_8), password) to summary
    }

    /** Lee el archivo, comprueba la contraseña y reemplaza los datos actuales de Ari. */
    fun restore(source: InputStream, database: AriDatabase, settings: SharedPreferences, password: CharArray): Summary {
        val json = JSONObject(String(decrypt(readLimited(source), password), Charsets.UTF_8))
        if (json.optInt("version") > FORMAT_VERSION) {
            throw IllegalStateException("Este respaldo es de una versión más nueva de Ari. Actualiza la app.")
        }
        val data = json.getJSONObject("data")
        database.importData(data)

        val editor = settings.edit()
        settings.all.keys.filter { it.startsWith(TEAM_PREFIX) || it.startsWith("candidates|") }
            .forEach { editor.remove(it) }
        val team = json.optJSONObject("team") ?: JSONObject()
        var teams = 0
        team.keys().forEach { key ->
            if (!key.startsWith(TEAM_PREFIX)) return@forEach
            val members = onlyPrivate(team.getString(key))
            if (members.length() > 0) {
                editor.putString(key, members.toString())
                teams++
            }
        }
        val networks = json.optJSONArray("approved_networks") ?: JSONArray()
        val approved = (0 until networks.length()).map { networks.getString(it) }.filter { it.isNotBlank() }.toSet()
        editor.putStringSet(KEY_APPROVED, approved)
        editor.apply()

        return Summary(
            data.getJSONArray("memories").length(),
            data.getJSONArray("messages").length(),
            teams,
            approved.size
        )
    }

    /** Por seguridad, aunque el archivo diga otra cosa, solo vuelven equipos de la red local. */
    private fun onlyPrivate(saved: String): JSONArray {
        val result = JSONArray()
        val array = runCatching { JSONArray(saved) }.getOrNull() ?: return result
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val host = runCatching { URI(item.getString("url")).host }.getOrNull() ?: continue
            if (NetworkGuard.isPrivateAddress(host)) result.put(item)
        }
        return result
    }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    private fun decrypt(file: ByteArray, password: CharArray): ByteArray {
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

    private fun readLimited(source: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FILE_BYTES) throw IllegalStateException("El archivo es demasiado grande para ser un respaldo de Ari.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
