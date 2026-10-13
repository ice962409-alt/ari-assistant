package com.ari.assistant

import android.content.SharedPreferences
import com.ari.assistant.agents.NetworkGuard
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI

/**
 * Respaldo de Ari en un archivo cifrado con contraseña: recuerdos, historial,
 * bitácora del equipo, equipo contratado y redes aprobadas. Sirve para
 * guardar una copia y para mudar a Ari a otro teléfono o a la laptop.
 *
 * El cifrado está en [BackupCrypto].
 */
object AriBackup {

    const val MIN_PASSWORD_LENGTH = 8
    const val FILE_EXTENSION = "ari"

    private const val MAX_FILE_BYTES = 64L * 1024 * 1024
    private const val FORMAT_VERSION = 1

    private const val KEY_APPROVED = "approved_networks"
    private const val TEAM_PREFIX = "team|"

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
        return BackupCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8), password) to summary
    }

    /** Lee el archivo, comprueba la contraseña y reemplaza los datos actuales de Ari. */
    fun restore(source: InputStream, database: AriDatabase, settings: SharedPreferences, password: CharArray): Summary {
        val json = JSONObject(String(BackupCrypto.decrypt(readLimited(source), password), Charsets.UTF_8))
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
