package com.ari.assistant

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Solo en la ARI del dueño: se actualiza desde GitHub.
 *
 * 1. Busca la versión privada más nueva de su rama en Releases ("ari-v<versión>", con el
 *    archivo ari.apk), que publica android.yml en cada compilación. Necesita que la llave de
 *    Ajustes → GitHub de ARI tenga el permiso Contents: lectura.
 * 2. Si ahí no hay nada, como antes: la última compilación en verde de su rama (GitHub Actions)
 *    y su artifact "ari-apk" (permiso Actions: lectura).
 * 3. Si es más nueva que la instalada, baja el APK y Android pide confirmar la instalación:
 *    ninguna app se puede actualizar sin que tú toques "Instalar", y Android solo la acepta si
 *    viene firmada con la misma clave.
 *
 * Nada de tus datos sale del teléfono: solo se piden archivos a GitHub.
 */
object AriUpdate {

    const val ARTIFACT = "ari-apk"
    /** La versión privada de Releases: etiqueta ari-v<versión> y su archivo (trabajo "publicar" de android.yml). */
    const val RELEASE_TAG_PREFIX = "ari-v"
    const val RELEASE_ASSET = "ari.apk"
    private const val WORKFLOW = "android.yml"

    /** "busca actualizaciones", "actualízate", "¿hay versión nueva?". */
    fun asks(text: String): Boolean {
        val t = com.ari.assistant.agents.normalizeForAgents(text)
        return Regex("^(?:ari,? )?(?:busca(?:r)? (?:una )?actualizaci(?:o|ó)n(?:es)?|actualizate|actualiza(?:te)? (?:la app|ari)|hay (?:una )?(?:version|actualizacion) nueva)\\??$").matches(t)
    }

    /** Una compilación es más nueva si su número de ejecución es mayor que la instalada. */
    fun isNewer(latestRun: Long, installedRun: Long) = installedRun == 0L || latestRun > installedRun

    /**
     * ¿La versión de Releases es más nueva que la instalada? Primero el número de versión
     * (0.69.0 > 0.68.0); con el mismo número, la compilación más reciente.
     */
    fun isNewerRelease(version: String, run: Long, installedVersion: String, installedRun: Long): Boolean {
        val order = compareVersions(version, installedVersion)
        return order > 0 || (order == 0 && isNewer(run, installedRun))
    }

    /** Compara "0.69.0" con "0.68.1" número por número (lo que no es número se ignora). */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val diff = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** El APK dentro del zip que entrega GitHub. */
    fun apkEntry(names: List<String>): String? = names.firstOrNull { it.endsWith(".apk") && !it.contains("/") }
        ?: names.firstOrNull { it.endsWith(".apk") }

    /** Qué significa cada respuesta de GitHub, en palabras. */
    fun explain(code: Int): String = when (code) {
        401 -> "La llave de GitHub no es válida o ya venció. Pega una nueva en Ajustes → GitHub de ARI."
        403, 404 -> "A la llave le falta un permiso de lectura: Contents (para las versiones de Releases) o Actions. En GitHub, edítala y agrégalos; luego vuelve a intentarlo."
        -1 -> "No pude conectarme a GitHub. Revisa tu internet."
        else -> "GitHub respondió con el código $code."
    }

    /**
     * Una compilación que se puede instalar. Si [assetId] es mayor que 0 viene de Releases (el
     * archivo ari.apk, de [size] bytes); si no, del artifact "ari-apk" de la ejecución [runId].
     */
    data class Release(
        val runId: Long,
        val number: Int,
        val title: String,
        val created: String,
        val assetId: Long = 0L,
        val size: Long = 0L,
        /** "0.69.0", de la etiqueta ari-v0.69.0 (vacío si viene de Actions). */
        val version: String = ""
    )

    sealed class Check {
        data class Available(val release: Release) : Check()
        object UpToDate : Check()
        object NoToken : Check()
        data class Failed(val message: String) : Check()
    }

    /**
     * La versión privada más nueva en la lista de Releases que manda GitHub ([json]), con su
     * ari.apk ya subido: la de número de versión más alto, de cualquier rama (cada sesión de
     * Claude trabaja en una rama nueva; si se filtrara por rama, ARI dejaría de ver las nuevas).
     * android.yml escribe en las notas "Compilación N (run ID), rama R, commit …": de ahí salen
     * el número y el run.
     */
    fun newestRelease(json: String): Release? {
        val list = runCatching { JSONArray(json) }.getOrNull() ?: return null
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            .filter { it.optString("tag_name").startsWith(RELEASE_TAG_PREFIX) && !it.optBoolean("draft") }
            .mapNotNull { release ->
                val notes = release.optString("body")
                val runId = Regex("\\(run (\\d+)\\)").find(notes)?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == RELEASE_ASSET && it.optString("state") == "uploaded" }
                    ?: return@mapNotNull null
                Release(
                    runId = runId,
                    number = Regex("Compilación (\\d+)").find(notes)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                    title = release.optString("name").take(120),
                    created = release.optString("published_at").take(10),
                    assetId = apk.optLong("id"),
                    size = apk.optLong("size"),
                    version = release.optString("tag_name").removePrefix(RELEASE_TAG_PREFIX)
                )
            }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version).takeIf { it != 0 } ?: a.runId.compareTo(b.runId) }
    }

    // --- Parte de Android ---

    /** Revisa si hay una compilación nueva. Corre fuera del hilo principal. */
    fun check(context: Context): Check {
        val token = AriGitHub.token(context) ?: return Check.NoToken
        val branch = BuildConfig.BUILD_BRANCH.ifEmpty { return Check.Failed("Esta versión no sabe de qué rama viene. Instala una compilada por GitHub.") }
        val (code, text) = get("https://api.github.com/repos/${AriGitHub.REPO}/releases?per_page=10", token)
        if (code == 401 || code == -1) return Check.Failed(explain(code))
        if (code in 200..299) {
            newestRelease(text)?.let { release ->
                val newer = isNewerRelease(release.version, release.runId, BuildConfig.VERSION_NAME, BuildConfig.BUILD_RUN)
                return if (newer) Check.Available(release) else Check.UpToDate
            }
        }
        val fromActions = checkActions(token, branch)
        // Sin el permiso Contents, Releases contesta 403 o 404: si Actions tampoco tiene un APK, se dice.
        if (code == 403 || code == 404) return fromActions as? Check.Available ?: Check.Failed(explain(code))
        return fromActions
    }

    /** Como antes de Releases: la última compilación en verde de la rama, si tiene su artifact. */
    private fun checkActions(token: String, branch: String): Check {
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
        if (release.assetId > 0) {
            return fetch(
                context, "https://api.github.com/repos/${AriGitHub.REPO}/releases/assets/${release.assetId}",
                token, "application/octet-stream", zipped = false, total = release.size, progress = progress
            )
        }
        val (code, text) = get("https://api.github.com/repos/${AriGitHub.REPO}/actions/runs/${release.runId}/artifacts", token)
        if (code !in 200..299) return null to explain(code)
        val artifacts = runCatching { JSONObject(text).getJSONArray("artifacts") }.getOrNull() ?: return null to "GitHub no mandó la lista de archivos."
        val artifact = (0 until artifacts.length()).mapNotNull { artifacts.optJSONObject(it) }
            .firstOrNull { it.optString("name") == ARTIFACT && !it.optBoolean("expired") }
            ?: return null to "Esa compilación ya no tiene el APK (GitHub los borra con el tiempo)."
        return fetch(
            context, artifact.getString("archive_download_url"), token, "application/vnd.github+json",
            zipped = true, total = artifact.optLong("size_in_bytes"), progress = progress
        )
    }

    /**
     * Pide [url] a GitHub. Lo normal es que conteste con una redirección a un enlace temporal
     * (la llave no se manda ahí); los archivos de Releases a veces llegan directo.
     */
    private fun fetch(
        context: Context, url: String, token: String, accept: String,
        zipped: Boolean, total: Long, progress: (Int) -> Unit
    ): Pair<File?, String?> {
        val first = URL(url).openConnection() as HttpURLConnection
        val location = try {
            first.instanceFollowRedirects = false
            headers(first, token)
            first.setRequestProperty("Accept", accept)
            first.readTimeout = 60_000
            val status = first.responseCode
            if (status in 200..299) return save(context, first.inputStream, zipped, total, progress)
            // Releases: si la versión se volvió a compilar, su archivo cambió y el de antes ya no existe.
            if (status == 404 && !zipped) return null to "Esa versión se reemplazó por una compilación más nueva. Dime otra vez \"busca actualizaciones\"."
            if (status !in 300..399) return null to explain(status)
            first.getHeaderField("Location") ?: return null to "GitHub no dio el enlace de descarga."
        } finally {
            first.disconnect()
        }
        if (!location.startsWith("https://")) return null to "El enlace de descarga no es seguro."

        val second = URL(location).openConnection() as HttpURLConnection
        try {
            second.connectTimeout = 15_000
            second.readTimeout = 60_000
            if (second.responseCode !in 200..299) return null to "La descarga falló (código ${second.responseCode})."
            return save(context, second.inputStream, zipped, total, progress)
        } finally {
            second.disconnect()
        }
    }

    /** Guarda el APK en la caché: tal cual (Releases) o sacándolo del zip (artifact). */
    private fun save(context: Context, input: InputStream, zipped: Boolean, total: Long, progress: (Int) -> Unit): Pair<File?, String?> {
        val folder = File(context.cacheDir, "actualizacion").apply { deleteRecursively(); mkdirs() }
        val apk = File(folder, "ari.apk")
        if (zipped) {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || apkEntry(listOf(entry.name)) == null) continue
                    copy(zip, apk, total, progress)
                    break
                }
            }
        } else {
            input.buffered().use { copy(it, apk, total, progress) }
        }
        if (!apk.isFile || apk.length() == 0L) return null to "El archivo de GitHub no traía el APK."
        return apk to null
    }

    private fun copy(input: InputStream, apk: File, total: Long, progress: (Int) -> Unit) {
        apk.outputStream().use { out ->
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
