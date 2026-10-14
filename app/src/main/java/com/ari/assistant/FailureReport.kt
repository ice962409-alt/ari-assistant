package com.ari.assistant

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Informe de fallos para quien prueba Ari.
 *
 * Si Ari se cierra sola, guarda en el teléfono qué pasó (el error y datos del teléfono).
 * La siguiente vez que se abre, ofrece mandarlo con "Compartir" (WhatsApp, correo…).
 * Sin permiso, Ari no lo envía sola: la persona ve el informe y decide. Si la persona
 * aceptó los informes automáticos, también lo manda [AutoReport] (solo datos técnicos).
 * Nunca incluye pláticas, recuerdos, contactos ni nombres.
 */
object FailureReport {

    /** Lo más largo que puede ser un informe: cabe en un mensaje de WhatsApp. */
    const val MAX_CHARS = 6000
    private const val MAX_TRACE_LINES = 40
    private const val MAX_MESSAGE = 300

    /** Arma el texto del informe (sin Android, para poder probarlo). */
    fun text(device: List<Pair<String, String>>, what: String, details: String, note: String = ""): String {
        val report = buildString {
            append("Informe de fallo de Ari\n")
            append("Qué pasó: ").append(what).append("\n")
            if (note.isNotBlank()) append("Lo que dice quien prueba: ").append(note.trim()).append("\n")
            append("\n")
            device.forEach { (name, value) -> append(name).append(": ").append(value).append("\n") }
            if (details.isNotBlank()) append("\nDetalle técnico:\n").append(details.trim()).append("\n")
        }
        return if (report.length > MAX_CHARS) report.take(MAX_CHARS) + "\n[recortado]" else report
    }

    /** El error y las líneas de código donde ocurrió. El mensaje se recorta: podría traer texto de la plática. */
    fun trace(error: Throwable): String {
        val out = StringBuilder()
        var current: Throwable? = error
        var depth = 0
        while (depth < 4) {
            val now = current ?: break
            if (depth > 0) out.append("Causado por: ")
            out.append(now.javaClass.name)
            now.message?.let { out.append(": ").append(it.replace('\n', ' ').take(MAX_MESSAGE)) }
            out.append("\n")
            now.stackTrace.take(MAX_TRACE_LINES / (depth + 1)).forEach { out.append("  at ").append(it).append("\n") }
            current = now.cause?.takeIf { it !== now }
            depth++
        }
        return out.toString()
    }

    // --- Parte de Android ---

    private const val FOLDER = "fallos"
    private const val FILE = "ultimo.txt"
    private const val PREFS = "ari_fallos"
    private const val KEY_SEEN_EXIT = "salida_vista"

    private fun file(context: Context) = File(File(context.filesDir, FOLDER).apply { mkdirs() }, FILE)

    /** Guarda los errores de Kotlin que cerrarían Ari, y luego deja que Android la cierre como siempre. */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                save(app, "Ari se cerró por un error (hilo ${thread.name})", trace(error))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun save(context: Context, what: String, details: String) {
        file(context).writeText(text(device(context), what, details))
        // Si la persona aceptó los informes automáticos, también queda en la cola para mandarse.
        AutoReport.record(context, what, details)
    }

    /** "Qué pasó" y el detalle técnico de un informe guardado (sin Android, para probarlo). */
    fun parts(report: String): Pair<String, String> {
        val what = report.lineSequence().firstOrNull { it.startsWith("Qué pasó: ") }?.removePrefix("Qué pasó: ").orEmpty()
        val details = report.substringAfter("\nDetalle técnico:\n", "").trim()
        return what.ifEmpty { "Fallo de Ari" } to details
    }

    /**
     * Fallos que Kotlin no alcanza a ver (la GPU, falta de memoria, Ari congelada):
     * desde Android 11 el sistema cuenta por qué se cerró la app la última vez.
     */
    fun checkSystemExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return
            val last = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (last.timestamp <= prefs.getLong(KEY_SEEN_EXIT, 0L)) return
            prefs.edit().putLong(KEY_SEEN_EXIT, last.timestamp).apply()
            val what = when (last.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Ari se cerró por un error del motor (por ejemplo, la GPU)"
                ApplicationExitInfo.REASON_ANR -> "Ari se congeló y Android la cerró"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "Android cerró a Ari por falta de memoria"
                ApplicationExitInfo.REASON_CRASH -> return // Ya lo guardó install().
                else -> return // Cerrarla tú, reinicios y actualizaciones no son fallos.
            }
            // Si ya hay un informe más nuevo de Kotlin, ese es el bueno.
            val saved = file(context)
            if (saved.isFile && saved.lastModified() >= last.timestamp) return
            val details = buildString {
                append("Fecha: ").append(java.util.Date(last.timestamp)).append("\n")
                last.description?.let { append("Android dice: ").append(it.take(MAX_MESSAGE)).append("\n") }
                append("Memoria usada: ").append(last.pss / 1024).append(" MB\n")
                if (last.reason == ApplicationExitInfo.REASON_ANR) {
                    // En una congelada, Android guarda qué estaba haciendo cada hilo (texto).
                    last.traceInputStream?.bufferedReader()?.use { append("\n").append(it.readText().take(3000)) }
                }
            }
            save(context, what, details)
        }
    }

    /** El informe que espera a que la persona decida, si hay uno. */
    fun pending(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Un informe que escribe quien prueba (Ajustes → Reportar un problema). */
    fun manual(context: Context, note: String): String {
        val last = pending(context)
        return text(device(context), "Reporte de quien prueba", last?.let { "Último fallo guardado:\n$it" } ?: "", note)
    }

    /** Datos del teléfono que ayudan a encontrar el fallo. Nada personal. */
    fun device(context: Context): List<Pair<String, String>> {
        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val ram = runCatching {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(0L)
        val model = ModelDownload(context).let { it.downloadedModel()?.label ?: if (it.downloadedFile() != null) "descargado (de antes)" else "elegido a mano o sin modelo" }
        val gpu = context.getSharedPreferences("ari_motor", Context.MODE_PRIVATE).getString("gpu", "")?.ifEmpty { "sin probar" }
        return listOf(
            "Versión de Ari" to version,
            "Teléfono" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "RAM" to "${ModelChoice.gb(ram)} GB",
            "Versión básica" to if (ModelChoice.basic(ram)) "sí" else "no",
            "Modelo" to model,
            "GPU" to (gpu ?: "?"),
        )
    }
}

/** La app entera: lo primero que corre al abrir Ari, para poder guardar un fallo desde el inicio. */
class AriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FailureReport.install(this)
    }
}
