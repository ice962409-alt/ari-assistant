package com.ari.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/**
 * Solo en ARI Trader: junta lo que el usuario necesita y lo que ARI no supo hacer, y lo manda
 * (con permiso) al mismo intermediario de los informes de fallos, marcado como propuesta.
 * Así el dueño y Claude lo ven, validan qué se puede y qué no, y deciden qué actualizar.
 *
 * ARI Trader no toca GitHub ni se actualiza sola: solo propone. Nada personal sale: únicamente
 * el texto de la propuesta y la versión de la app.
 */
object TraderProposals {

    const val MARK = "[Propuesta ARI Trader] "
    private const val MAX_NOTE = 240
    private const val MAX_NOTES = 30
    private const val MAX_QUEUE = 20
    private const val MAX_BODY = 32 * 1024

    private const val PREFS = "ari_trader_mejoras"
    private const val KEY_ON = "envio_activo"
    private const val KEY_ASKED = "preguntado"
    private const val KEY_INSTALL = "instalacion"
    private const val NOTES_KEY = "notas"
    private const val QUEUE_FILE = "trader_propuestas.json"

    // --- Lo que no necesita Android (para probarlo) ---

    private val NOTE = Regex("^(?:anota|apunta|guarda)\\s+(?:una\\s+|esta\\s+)?(?:mejora|idea|propuesta)(?:\\s+de\\s+mejora)?[:,]?\\s+(.+)$|^mejora:\\s*(.+)$")
    private val PROPOSE = Regex("^(?:propon|proponme|dame|arma|envia|manda)(?:\\s+(?:unas?|las|tus))?\\s+(?:mejoras?|propuestas?(?:\\s+de\\s+mejora)?)$|^que\\s+(?:te|le)\\s+(?:falta|mejorarias)$")
    private val CLEAR = Regex("^(?:borra|olvida|limpia)\\s+(?:las\\s+|tus\\s+)?(?:mejoras|propuestas)$")

    sealed class Request {
        data class Note(val text: String) : Request()
        object Propose : Request()
        object Clear : Request()
    }

    fun parse(normalized: String): Request? {
        NOTE.matchEntire(normalized)?.let { m ->
            val note = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            return if (note.length >= 5) Request.Note(note.take(MAX_NOTE)) else null
        }
        if (PROPOSE.matches(normalized)) return Request.Propose
        if (CLEAR.matches(normalized)) return Request.Clear
        return null
    }

    /** La instrucción para Gemma: que marque lo que al usuario le falta para proponerlo. */
    fun instruction(): String =
        "Si el usuario necesita algo de trading que todavía no puedes hacer, o pide una función que te falta, " +
            "escribe al final una línea aparte que empiece con \"MEJORA:\" y diga en pocas palabras qué le hace falta. " +
            "Máximo una por respuesta y solo si es una mejora real para ayudarle en su trading."

    fun notesIn(response: String): List<String> =
        response.lines().map { it.trim().trim('*', '_', ' ') }
            .filter { it.uppercase().startsWith("MEJORA:") }
            .map { it.substringAfter(':').trim().trimEnd('.').take(MAX_NOTE) }
            .filter { it.length >= 5 }

    fun add(notes: List<String>, note: String): List<String> =
        (notes + note.trim()).distinctBy { it.lowercase() }.takeLast(MAX_NOTES)

    /** El cuerpo JSON de una propuesta, igual que el contrato de los informes (kind=error). */
    fun body(fields: Map<String, String>): String {
        val json = fields.filterValues { it.isNotEmpty() }.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"" + AutoReport.escape(k) + "\":\"" + AutoReport.escape(v) + "\""
        }
        if (json.toByteArray(Charsets.UTF_8).size <= MAX_BODY) return json
        val details = fields["details"].orEmpty()
        if (details.isEmpty()) return json
        return body(fields + ("details" to details.take(details.length / 2)))
    }

    // --- Android ---

    fun enabled(context: Context) = prefs(context).getBoolean(KEY_ON, false)
    fun asked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).putBoolean(KEY_ASKED, true).apply()
        if (!on) queueFile(context).delete()
    }

    fun notes(context: Context): List<String> =
        prefs(context).getString(NOTES_KEY, "")!!.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun saveNotes(context: Context, notes: List<String>) {
        prefs(context).edit().putString(NOTES_KEY, notes.joinToString("\n")).apply()
    }

    private fun installId(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_INSTALL, null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL, it).apply() }
    }

    /** Pone las notas en la cola para mandarlas. Devuelve cuántas quedaron por enviar. */
    @Synchronized
    fun enqueue(context: Context, notes: List<String>): Int {
        if (notes.isEmpty()) return 0
        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        val summary = (MARK + notes.first()).take(200)
        val details = notes.joinToString("\n") { "- $it" }
        val fields = mapOf(
            "platform" to "android",
            "appVersion" to AutoReport.version(version),
            "kind" to "error",
            "summary" to summary,
            "details" to AutoReport.clean(details),
            "installId" to installId(context),
            "happenedAt" to Instant.now().toString().take(19) + "Z",
        )
        val queue = readQueue(context)
        queue.put(JSONObject().put("body", body(fields)).put("attempts", 0).put("nextAt", 0L))
        while (queue.length() > MAX_QUEUE) queue.remove(0)
        writeQueue(context, queue)
        return queue.length()
    }

    /** Manda lo que esté en la cola y le toque. Fuera del hilo principal. */
    @Synchronized
    fun flush(context: Context): Int {
        if (!enabled(context)) return 0
        val queue = runCatching { readQueue(context) }.getOrNull() ?: return 0
        val now = System.currentTimeMillis()
        val keep = JSONArray()
        var sent = 0
        for (i in 0 until queue.length()) {
            val entry = queue.optJSONObject(i) ?: continue
            if (entry.optLong("nextAt") > now) { keep.put(entry); continue }
            val code = runCatching { post(entry.getString("body")) }.getOrDefault(-1)
            when {
                code in 200..299 -> sent++
                AutoReport.done(code) -> {}
                else -> {
                    val attempts = entry.optInt("attempts")
                    keep.put(entry.put("attempts", attempts + 1).put("nextAt", now + AutoReport.backoffMillis(attempts)))
                }
            }
        }
        writeQueue(context, keep)
        return sent
    }

    private fun post(body: String): Int {
        val c = URL(BuildConfig.INFORMES_URL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("X-Ari-App", "ari")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return c.responseCode
        } finally {
            c.disconnect()
        }
    }

    private fun queueFile(context: Context) = File(File(context.filesDir, "fallos").apply { mkdirs() }, QUEUE_FILE)

    private fun readQueue(context: Context): JSONArray {
        val f = queueFile(context)
        if (!f.isFile) return JSONArray()
        return runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray())
    }

    private fun writeQueue(context: Context, queue: JSONArray) {
        val f = queueFile(context)
        if (queue.length() == 0) f.delete() else f.writeText(queue.toString())
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
