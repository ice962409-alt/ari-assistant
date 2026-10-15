package com.ari.assistant

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Solo ARI Trader: se mantiene al día sola, sin GitHub ni llaves.
 *
 * Consulta un archivo público (BuildConfig.TRADER_UPDATE_URL) que dice cuál es la última versión
 * y de dónde bajarla. Si hay una más nueva, avisa; el usuario toca "Bajar" y Android le pide
 * confirmar la instalación (solo acepta la que viene firmada igual). Tus datos no salen del teléfono.
 */
object TraderUpdate {

    data class Latest(val code: Long, val name: String, val url: String, val notes: String)

    /** Lee el archivo de versión. Sin él (o mal escrito) no hay actualización. */
    fun parse(json: String): Latest? = runCatching {
        val o = JSONObject(json)
        val url = o.optString("apkUrl")
        if (!url.startsWith("https://")) return null
        Latest(
            code = o.optLong("versionCode"),
            name = o.optString("versionName"),
            url = url,
            notes = o.optString("notes").take(300)
        )
    }.getOrNull()

    /** Hay actualización si la versión publicada tiene un número mayor que la instalada. */
    fun isNewer(latestCode: Long, installedCode: Long) = latestCode > installedCode

    // --- Android ---

    sealed class Check {
        data class Available(val latest: Latest) : Check()
        object UpToDate : Check()
        data class Failed(val message: String) : Check()
    }

    private fun installedCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    fun check(context: Context): Check {
        val url = BuildConfig.TRADER_UPDATE_URL
        if (url.isBlank()) return Check.Failed("Todavía no hay un lugar de actualizaciones configurado.")
        val (code, text) = get(url)
        if (code !in 200..299) return Check.Failed("No pude revisar si hay versión nueva (código $code).")
        val latest = parse(text) ?: return Check.UpToDate
        return if (isNewer(latest.code, installedCode(context))) Check.Available(latest) else Check.UpToDate
    }

    /** Baja el APK a la caché. Devuelve el archivo o un mensaje de error. */
    fun download(context: Context, latest: Latest, progress: (Int) -> Unit): Pair<File?, String?> {
        val folder = File(context.cacheDir, "actualizacion").apply { deleteRecursively(); mkdirs() }
        val apk = File(folder, "ari-trader.apk")
        val c = URL(latest.url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = true
            if (c.responseCode !in 200..299) return null to "La descarga falló (código ${c.responseCode})."
            val total = c.contentLengthLong
            apk.outputStream().use { out ->
                c.inputStream.buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) progress(((read * 100) / total).toInt().coerceIn(0, 99))
                    }
                }
            }
        } catch (e: Exception) {
            return null to "No pude bajar la actualización. Revisa tu internet."
        } finally {
            c.disconnect()
        }
        if (!apk.isFile || apk.length() == 0L) return null to "La descarga quedó incompleta."
        // Reusa las comprobaciones de AriUpdate: mismo paquete y no una versión más vieja.
        AriUpdate.verify(context, apk)?.let { return null to it }
        return apk to null
    }

    private fun get(url: String): Pair<Int, String> = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "AriTrader")
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to stream?.bufferedReader()?.use { it.readText().take(20_000) }.orEmpty()
        } finally {
            c.disconnect()
        }
    }.getOrDefault(-1 to "")
}
