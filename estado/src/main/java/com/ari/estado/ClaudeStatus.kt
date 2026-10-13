package com.ari.estado

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

/** Lo que Claude publica en estado.json del repositorio privado Ari-estado. */
data class ClaudeStatus(
    val active: Boolean,
    val task: String,
    val step: Int,
    val steps: Int,
    val progress: Int,
    val next: String,
    val pending: Int,
    val updatedAt: Instant?,
    /** Enlace a la sesión de Claude Code donde se escribe "sigue". */
    val session: String
) {
    /**
     * Claude dice que trabaja, pero no actualiza desde hace mucho: lo más
     * probable es que su sesión se haya cerrado y haya que escribirle "sigue".
     */
    fun isStale(now: Instant = Instant.now()): Boolean =
        active && updatedAt != null && Duration.between(updatedAt, now) > STALE_AFTER

    companion object {
        val STALE_AFTER: Duration = Duration.ofMinutes(45)

        fun parse(json: String): ClaudeStatus {
            val o = JSONObject(json)
            return ClaudeStatus(
                active = o.optBoolean("activo", false),
                task = o.optString("haciendo", ""),
                step = o.optInt("paso", 0),
                steps = o.optInt("pasos", 0),
                progress = o.optInt("progreso", 0).coerceIn(0, 100),
                next = o.optString("siguiente", ""),
                pending = o.optInt("pendientes", 0),
                updatedAt = runCatching { Instant.parse(o.optString("actualizado")) }.getOrNull(),
                // Solo enlaces de claude.ai: el estado no puede mandar al usuario a otro sitio.
                session = o.optString("sesion", "").takeIf { it.startsWith("https://claude.ai/") }.orEmpty()
            )
        }
    }
}

/** Resultado de la última consulta, guardado para que el widget lo dibuje. */
object StatusStore {
    private const val PREFS = "ari_estado_status"

    fun saveStatus(context: Context, json: String) {
        prefs(context).edit()
            .putString("json", json)
            .putLong("fetchedAt", System.currentTimeMillis())
            .remove("error")
            .apply()
    }

    fun saveError(context: Context, message: String) {
        prefs(context).edit().putString("error", message).apply()
    }

    fun status(context: Context): ClaudeStatus? =
        prefs(context).getString("json", null)?.let { runCatching { ClaudeStatus.parse(it) }.getOrNull() }

    fun error(context: Context): String? = prefs(context).getString("error", null)

    /** Lo último que se avisó, para no repetir la misma notificación. */
    fun lastAlert(context: Context): String? = prefs(context).getString("lastAlert", null)

    fun setLastAlert(context: Context, key: String?) {
        prefs(context).edit().putString("lastAlert", key).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Lee estado.json con la API de GitHub, por HTTPS y con la llave de solo
 * lectura. Es la única conexión a internet de la app.
 */
object StatusFetcher {
    private const val URL_STATUS =
        "https://api.github.com/repos/javierhedzd-ops/Ari-estado/contents/estado.json"
    private const val MAX_BYTES = 64 * 1024

    /** Devuelve el JSON de estado, o lanza una excepción con un mensaje para el usuario. */
    fun fetch(token: String): String {
        val connection = URL(URL_STATUS).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github.raw+json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "AriEstado")

            when (val code = connection.responseCode) {
                in 200..299 -> {}
                401 -> throw IllegalStateException("La llave no es válida o ya venció. Crea una nueva.")
                403 -> throw IllegalStateException("GitHub rechazó la llave (sin permiso o demasiadas consultas).")
                404 -> throw IllegalStateException("La llave no tiene acceso a Ari-estado, o falta estado.json.")
                else -> throw IllegalStateException("GitHub respondió con el código $code.")
            }

            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    if (buffer.size() + read > MAX_BYTES) throw IllegalStateException("El estado es demasiado grande.")
                    buffer.write(chunk, 0, read)
                }
            }
            val json = buffer.toString(Charsets.UTF_8.name())
            ClaudeStatus.parse(json) // comprobar que es un estado válido antes de guardarlo
            return json
        } finally {
            connection.disconnect()
        }
    }
}
