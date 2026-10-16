package com.ari.assistant

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/**
 * Informes de fallos automáticos (contrato en el repositorio ari-informes, CONTRATO.md).
 *
 * - Apagado hasta que la persona acepta. Sin permiso, sigue el informe por correo de [FailureReport].
 * - Solo datos técnicos: el error, la versión y el teléfono. Nunca pláticas, recuerdos, la
 *   libreta, contactos, nombres, adjuntos, capturas ni la voz. Antes de mandarlo se borran
 *   correos, teléfonos y enlaces que se hayan colado en el mensaje del error.
 * - Sin red no pasa nada: se guarda en una cola (máximo 20) y se reintenta después.
 * - Va al intermediario (BuildConfig.INFORMES_URL), que lo deja como ficha en ari-informes.
 */
object AutoReport {

    const val MAX_QUEUE = 20
    private const val MAX_SUMMARY = 200
    private const val MAX_DETAILS = 8000
    private const val MAX_BODY = 32 * 1024

    enum class Kind(val wire: String) { CIERRE("cierre"), CONGELADA("congelada"), MEMORIA("memoria"), ERROR("error") }

    /** De qué tipo es el fallo, según lo que guardó [FailureReport]. */
    fun kind(what: String): Kind = when {
        what.contains("congel", ignoreCase = true) -> Kind.CONGELADA
        what.contains("memoria", ignoreCase = true) -> Kind.MEMORIA
        what.contains("se cerró", ignoreCase = true) -> Kind.CIERRE
        else -> Kind.ERROR
    }

    /** Una línea que resume el fallo: el primer error de la traza o, si no hay, qué pasó. */
    fun summary(what: String, details: String): String {
        val first = details.lineSequence().map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("at ") && !it.startsWith("Fecha:") && !it.startsWith("Memoria usada:") }
        return clean(first?.removePrefix("Android dice: ") ?: what).take(MAX_SUMMARY).ifBlank { what.take(MAX_SUMMARY) }
    }

    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val URL_TEXT = Regex("(?i)\\b(?:https?|content|file)://\\S+")
    private val PHONE = Regex("\\+?\\d[\\d\\s().-]{7,}\\d")
    private val TOKEN = Regex("\\b(?:github_pat_|ghp_|sk-)[A-Za-z0-9_\\-]{8,}")

    /** Quita lo que pudiera ser personal y se haya colado en el mensaje de un error. */
    fun clean(text: String): String = text
        .replace(TOKEN, "[llave]")
        .replace(EMAIL, "[correo]")
        .replace(URL_TEXT, "[enlace]")
        .replace(PHONE) { match -> if (match.value.count { it.isDigit() } >= 8) "[número]" else match.value }

    /** Solo letras, números y . + _ - (como pide el intermediario). */
    fun version(raw: String): String = raw.filter { it.isLetterOrDigit() || it in ".+_-" }.ifEmpty { "0" }

    /** Cuánto esperar antes del siguiente intento: 1 minuto, 10 minutos y después cada hora. */
    fun backoffMillis(attempts: Int): Long = when (attempts) {
        0 -> 60_000L
        1 -> 10 * 60_000L
        else -> 60 * 60_000L
    }

    /** El cuerpo JSON del informe (escrito a mano para poder probarlo sin Android). */
    fun body(fields: Map<String, String>): String {
        val json = fields.filterValues { it.isNotEmpty() }.entries.joinToString(",", "{", "}") { (key, value) ->
            "\"" + escape(key) + "\":\"" + escape(value) + "\""
        }
        if (json.toByteArray(Charsets.UTF_8).size <= MAX_BODY) return json
        // Más de 32 KB: se recorta el detalle.
        val details = fields["details"].orEmpty()
        if (details.isEmpty()) return json
        return body(fields + ("details" to details.take(details.length / 2)))
    }

    fun escape(value: String): String = buildString {
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }

    /** Qué hacer con la respuesta: true si el informe ya no se debe reintentar. */
    fun done(code: Int): Boolean = code in 200..299 || code == 400 || code == 403 || code == 413

    // --- Parte de Android ---

    private const val PREFS = "ari_informes_auto"
    private const val KEY_ON = "activo"
    private const val KEY_ASKED = "preguntado"
    private const val KEY_INSTALL = "instalacion"
    private const val QUEUE_FILE = "cola_informes.json"
    private const val JOB_ID = 4_207

    fun enabled(context: Context) = prefs(context).getBoolean(KEY_ON, false)

    fun asked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).putBoolean(KEY_ASKED, true).apply()
        if (!on) queueFile(context).delete()
    }

    /** Un número al azar de esta instalación, sin relación con la persona. */
    private fun installId(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_INSTALL, null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL, it).apply() }
    }

    /** Guarda el informe en la cola (solo si la persona aceptó). No usa la red: sirve en pleno cierre. */
    @Synchronized
    fun record(context: Context, what: String, details: String) {
        if (!enabled(context)) return
        runCatching {
            val ram = runCatching {
                val info = ActivityManager.MemoryInfo()
                context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
                ModelChoice.gb(info.totalMem)
            }.getOrDefault("")
            val appVersion = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
            val device = FailureReport.device(context).toMap()
            val model = listOfNotNull(device["Modelo"], device["GPU"]?.let { "GPU: $it" }).joinToString(" · ")
            val fields = mapOf(
                "platform" to "android",
                "appVersion" to version(appVersion),
                "kind" to kind(what).wire,
                "summary" to summary(what, details),
                "details" to clean("$what\n\n$details").take(MAX_DETAILS),
                "osVersion" to "Android ${Build.VERSION.RELEASE}",
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "ramGb" to ram,
                "model" to model,
                "installId" to installId(context),
                "happenedAt" to Instant.now().toString().take(19) + "Z",
            )
            val queue = readQueue(context)
            queue.put(JSONObject().put("body", body(fields)).put("attempts", 0).put("nextAt", 0L))
            while (queue.length() > MAX_QUEUE) queue.remove(0)
            writeQueue(context, queue)
        }
    }

    fun pendingCount(context: Context) = runCatching { readQueue(context).length() }.getOrDefault(0)

    /** Manda lo que esté en la cola y le toque. Corre fuera del hilo principal. */
    @Synchronized
    fun flush(context: Context): Int {
        if (!enabled(context)) return 0
        val queue = runCatching { readQueue(context) }.getOrNull() ?: return 0
        val now = System.currentTimeMillis()
        val keep = JSONArray()
        var sent = 0
        for (i in 0 until queue.length()) {
            val entry = queue.optJSONObject(i) ?: continue
            if (entry.optLong("nextAt") > now) {
                keep.put(entry)
                continue
            }
            val code = runCatching { post(entry.getString("body")) }.getOrDefault(-1)
            when {
                code in 200..299 -> sent++
                done(code) -> {}
                else -> {
                    val attempts = entry.optInt("attempts")
                    keep.put(entry.put("attempts", attempts + 1).put("nextAt", now + backoffMillis(attempts)))
                }
            }
        }
        writeQueue(context, keep)
        if (keep.length() > 0) schedule(context, keep)
        return sent
    }

    private fun post(body: String): Int {
        val connection = URL(BuildConfig.INFORMES_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("X-Ari-App", "ari")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /** Reintento cuando haya red, a la hora del siguiente pendiente. */
    private fun schedule(context: Context, queue: JSONArray) {
        val next = (0 until queue.length()).mapNotNull { queue.optJSONObject(it)?.optLong("nextAt") }.minOrNull() ?: return
        val delay = (next - System.currentTimeMillis()).coerceAtLeast(30_000L)
        runCatching {
            context.getSystemService(JobScheduler::class.java)?.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, AutoReportJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(delay)
                    .setPersisted(false)
                    .build()
            )
        }
    }

    private fun queueFile(context: Context) = File(File(context.filesDir, "fallos").apply { mkdirs() }, QUEUE_FILE)

    private fun readQueue(context: Context): JSONArray {
        val file = queueFile(context)
        if (!file.isFile) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
    }

    private fun writeQueue(context: Context, queue: JSONArray) {
        val file = queueFile(context)
        if (queue.length() == 0) file.delete() else file.writeText(queue.toString())
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Reintenta mandar los informes de la cola cuando vuelve la red. */
class AutoReportJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { AutoReport.flush(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
