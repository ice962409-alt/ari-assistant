package com.ari.assistant

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Solo en la ARI del dueño: se actualiza desde GitHub.
 *
 * 1. Busca la última compilación en verde de su rama (GitHub Actions) con la llave de
 *    Ajustes → GitHub de ARI (necesita el permiso Actions: lectura).
 * 2. Si es más nueva que la instalada, baja el APK (artifact "ari-apk").
 * 3. Android pide confirmar la instalación: ninguna app se puede actualizar sin que tú toques
 *    "Instalar", y Android solo la acepta si viene firmada con la misma clave.
 *
 * Nada de tus datos sale del teléfono: solo se piden archivos a GitHub.
 */
object AriUpdate {

    const val ARTIFACT = "ari-apk"
    private const val WORKFLOW = "android.yml"

    /** "busca actualizaciones", "actualízate", "¿hay versión nueva?". */
    fun asks(text: String): Boolean {
        val t = com.ari.assistant.agents.normalizeForAgents(text)
        return Regex("^(?:ari,? )?(?:busca(?:r)? (?:una )?actualizaci(?:o|ó)n(?:es)?|actualizate|actualiza(?:te)? (?:la app|ari)|hay (?:una )?(?:version|actualizacion) nueva)\\??$").matches(t)
    }

    /** Una compilación es más nueva si su número de ejecución es mayor que la instalada. */
    fun isNewer(latestRun: Long, installedRun: Long) = installedRun == 0L || latestRun > installedRun

    /** El APK dentro del zip que entrega GitHub. */
    fun apkEntry(names: List<String>): String? = names.firstOrNull { it.endsWith(".apk") && !it.contains("/") }
        ?: names.firstOrNull { it.endsWith(".apk") }

    /** Qué significa cada respuesta de GitHub, en palabras. */
    fun explain(code: Int): String = when (code) {
        401 -> "La llave de GitHub no es válida o ya venció. Pega una nueva en Ajustes → GitHub de ARI."
        403, 404 -> "A la llave le falta el permiso Actions (lectura). En GitHub, edítala y agrégalo; luego vuelve a intentarlo."
        -1 -> "No pude conectarme a GitHub. Revisa tu internet."
        else -> "GitHub respondió con el código $code."
    }

    data class Release(val runId: Long, val number: Int, val title: String, val created: String)

    sealed class Check {
        data class Available(val release: Release) : Check()
        object UpToDate : Check()
        object NoToken : Check()
        data class Failed(val message: String) : Check()
    }

    // --- Parte de Android ---

    /** Revisa si hay una compilación nueva. Corre fuera del hilo principal. */
    fun check(context: Context): Check {
        val token = AriGitHub.token(context) ?: return Check.NoToken
        val branch = BuildConfig.BUILD_BRANCH.ifEmpty { return Check.Failed("Esta versión no sabe de qué rama viene. Instala una compilada por GitHub.") }
        val url = "https://api.github.com/repos/${AriGitHub.REPO}/actions/workflows/$WORKFLOW/runs" +
            "?branch=${java.net.URLEncoder.encode(branch, "UTF-8")}&status=success&per_page=1"
        val (code, text) = get(url, token)
        if (code !in 200..299) return Check.Failed(explain(code))
        val run = runCatching { JSONObject(text).getJSONArray("workflow_runs").optJSONObject(0) }.getOrNull()
            ?: return Check.UpToDate
        val release = Release(
            runId = run.optLong("id"),
            number = run.optInt("run_number"),
            title = run.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.firstOrNull().orEmpty().take(120),
            created = run.optString("created_at").take(10)
        )
        return if (isNewer(release.runId, BuildConfig.BUILD_RUN)) Check.Available(release) else Check.UpToDate
    }

    /** Baja el APK de esa compilación. Devuelve el archivo o un mensaje de error. */
    fun download(context: Context, release: Release, progress: (Int) -> Unit): Pair<File?, String?> {
        val token = AriGitHub.token(context) ?: return null to explain(401)
        val (code, text) = get("https://api.github.com/repos/${AriGitHub.REPO}/actions/runs/${release.runId}/artifacts", token)
        if (code !in 200..299) return null to explain(code)
        val artifacts = runCatching { JSONObject(text).getJSONArray("artifacts") }.getOrNull() ?: return null to "GitHub no mandó la lista de archivos."
        val artifact = (0 until artifacts.length()).mapNotNull { artifacts.optJSONObject(it) }
            .firstOrNull { it.optString("name") == ARTIFACT && !it.optBoolean("expired") }
            ?: return null to "Esa compilación ya no tiene el APK (GitHub los borra con el tiempo)."
        val total = artifact.optLong("size_in_bytes")

        // GitHub contesta con una redirección a un enlace temporal; la llave no se manda ahí.
        val first = URL(artifact.getString("archive_download_url")).openConnection() as HttpURLConnection
        val location = try {
            first.instanceFollowRedirects = false
            headers(first, token)
            val status = first.responseCode
            if (status !in 300..399) return null to explain(status)
            first.getHeaderField("Location") ?: return null to "GitHub no dio el enlace de descarga."
        } finally {
            first.disconnect()
        }
        if (!location.startsWith("https://")) return null to "El enlace de descarga no es seguro."

        val folder = File(context.cacheDir, "actualizacion").apply { deleteRecursively(); mkdirs() }
        val apk = File(folder, "ari.apk")
        val second = URL(location).openConnection() as HttpURLConnection
        try {
            second.connectTimeout = 15_000
            second.readTimeout = 60_000
            if (second.responseCode !in 200..299) return null to "La descarga falló (código ${second.responseCode})."
            ZipInputStream(second.inputStream.buffered()).use { zip ->
                var read = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || apkEntry(listOf(entry.name)) == null) continue
                    apk.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            read += n
                            if (total > 0) progress(((read * 100) / total).toInt().coerceIn(0, 99))
                        }
                    }
                    break
                }
            }
        } finally {
            second.disconnect()
        }
        if (!apk.isFile || apk.length() == 0L) return null to "El archivo de GitHub no traía el APK."
        return apk to null
    }

    /** Revisa que el APK sea ARI y no una versión más vieja. */
    fun verify(context: Context, apk: File): String? {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return "El archivo no es una app válida."
        if (info.packageName != context.packageName) return "El archivo no es ARI; no lo instalo."
        val current = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val newCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        @Suppress("DEPRECATION")
        val oldCode = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
        if (newCode < oldCode) return "Esa versión es más vieja que la tuya; no la instalo."
        return null
    }

    /** ¿Android ya deja que ARI instale actualizaciones? Si no, hay que darle el permiso una vez. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context) =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))

    /** Le pasa el APK al instalador de Android, que te pide confirmar. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("ari.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, id, Intent(context, AriUpdateReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    private fun headers(connection: HttpURLConnection, token: String) {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "ARI")
    }

    private fun get(url: String, token: String): Pair<Int, String> = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            headers(c, token)
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to stream?.bufferedReader()?.use { it.readText().take(200_000) }.orEmpty()
        } finally {
            c.disconnect()
        }
    }.getOrDefault(-1 to "")
}

/** El instalador de Android avisa: pide tu confirmación, terminó o falló. */
class AriUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                runCatching {
                    android.widget.Toast.makeText(context, "No se instaló la actualización. $message".trim(), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
