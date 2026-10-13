package com.ari.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File

/**
 * Descarga el modelo Gemma desde su página oficial con el gestor de descargas
 * de Android. Es la única vez que Ari usa internet, y solo cuando el usuario
 * lo pide: el sistema baja el archivo (aunque Ari se cierre), lo retoma si se
 * corta y muestra el avance en las notificaciones.
 */
class ModelDownload(context: Context) {

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(DownloadManager::class.java)
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    sealed class State {
        object None : State()
        data class Running(val downloaded: Long, val total: Long) : State()
        data class Paused(val reason: Int, val downloaded: Long, val total: Long) : State()
        data class Done(val file: File) : State()
        data class Failed(val reason: Int) : State()
    }

    sealed class Start {
        object Started : Start()
        data class NoSpace(val free: Long) : Start()
        object Unavailable : Start()
    }

    private fun folder(): File? = context.getExternalFilesDir(FOLDER)

    /** El modelo ya descargado y completo, si existe. */
    fun downloadedFile(): File? =
        folder()?.let { File(it, FILE_NAME) }?.takeIf { it.isFile && it.length() >= ModelDownloadText.MIN_VALID_BYTES }

    fun isRunning(): Boolean = prefs.getLong(KEY_ID, -1L) >= 0

    /** Espacio libre donde se guarda el modelo (0 si no se puede saber). */
    fun freeSpace(): Long {
        val dir = folder() ?: return 0L
        dir.mkdirs()
        return runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)
    }

    /** Cuál de los modelos se descargó (null si se eligió a mano o es de antes de 0.16.0). */
    fun downloadedModel(): ModelChoice.Model? = ModelChoice.Model.byId(prefs.getString(KEY_MODEL, null))

    fun start(model: ModelChoice.Model): Start {
        val dir = folder() ?: return Start.Unavailable
        dir.mkdirs()
        val free = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)
        if (free < model.spaceNeeded) return Start.NoSpace(free)
        File(dir, PART_NAME).delete()

        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Ari: descargando su modelo")
            .setDescription("${model.label} (unos ${ModelDownloadText.gb(model.bytes)} GB)")
            .setDestinationInExternalFilesDir(context, FOLDER, PART_NAME)
            .setAllowedOverMetered(false) // Gigas de descarga: solo por Wi-Fi, para no gastar tus datos.
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = manager.enqueue(request)
        prefs.edit().putLong(KEY_ID, id).putString(KEY_DOWNLOADING, model.id).apply()
        return Start.Started
    }

    fun state(): State {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id < 0) return downloadedFile()?.let { State.Done(it) } ?: State.None
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                forget()
                return State.None
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                .takeIf { it > 0 } ?: (ModelChoice.Model.byId(prefs.getString(KEY_DOWNLOADING, null)) ?: ModelChoice.Model.E4B).bytes
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> finish()
                DownloadManager.STATUS_FAILED -> {
                    forget()
                    folder()?.let { File(it, PART_NAME).delete() }
                    State.Failed(reason)
                }
                DownloadManager.STATUS_PAUSED -> State.Paused(reason, done, total)
                else -> State.Running(done, total)
            }
        }
    }

    /** Termina: el archivo a medias pasa a ser el modelo, solo si está completo. */
    private fun finish(): State {
        val model = prefs.getString(KEY_DOWNLOADING, null)
        forget()
        val dir = folder() ?: return State.Failed(ModelDownloadText.REASON_TOO_SMALL)
        val part = File(dir, PART_NAME)
        val final = File(dir, FILE_NAME)
        if (!part.isFile || part.length() < ModelDownloadText.MIN_VALID_BYTES) {
            part.delete()
            return downloadedFile()?.let { State.Done(it) } ?: State.Failed(ModelDownloadText.REASON_TOO_SMALL)
        }
        final.delete()
        if (!part.renameTo(final)) return State.Failed(ModelDownloadText.REASON_TOO_SMALL)
        prefs.edit().putString(KEY_MODEL, model).apply()
        return State.Done(final)
    }

    fun cancel() {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id >= 0) manager.remove(id)
        forget()
        folder()?.let { File(it, PART_NAME).delete() }
    }

    /** Borra el modelo descargado (por ejemplo, si eliges otro archivo a mano). */
    fun deleteDownloaded() {
        folder()?.let { File(it, FILE_NAME).delete() }
        prefs.edit().remove(KEY_MODEL).apply()
    }

    private fun forget() {
        prefs.edit().remove(KEY_ID).remove(KEY_DOWNLOADING).apply()
    }

    companion object {
        private const val PREFS = "ari_descarga"
        private const val KEY_ID = "id"
        private const val KEY_DOWNLOADING = "bajando"
        private const val KEY_MODEL = "modelo"
        private const val FOLDER = "modelos"
        private const val FILE_NAME = "gemma-model.litertlm"
        private const val PART_NAME = "gemma-model.litertlm.descarga"
    }
}

/** Textos y reglas de la descarga, sin Android, para poder probarlos. */
object ModelDownloadText {
    /** Un archivo menor que esto no es el modelo (por ejemplo, una página de error). */
    const val MIN_VALID_BYTES = 1_000_000_000L
    const val REASON_TOO_SMALL = -1

    private const val GB = 1_000_000_000.0

    fun gb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / GB)

    fun progress(downloaded: Long, total: Long): String {
        val percent = if (total > 0) (downloaded * 100 / total).coerceIn(0, 100) else 0
        return "Descargando a Gemma: $percent% (${gb(downloaded)} de ${gb(total)} GB)"
    }

    /** Motivos de pausa de DownloadManager: 1 reintento, 2 sin red, 3 esperando Wi-Fi. */
    fun paused(reason: Int, downloaded: Long, total: Long): String = when (reason) {
        3 -> "Descarga en pausa: esperando Wi-Fi (${gb(downloaded)} de ${gb(total)} GB). No uso tus datos móviles."
        2 -> "Descarga en pausa: sin conexión. Seguirá sola cuando vuelva la red."
        1 -> "Descarga en pausa: se cortó y la reintento en un momento."
        else -> "Descarga en pausa (${gb(downloaded)} de ${gb(total)} GB)."
    }

    /** Motivos de error: códigos HTTP (401, 403, 404…) o de DownloadManager (1006 sin espacio…). */
    fun failed(reason: Int): String = when (reason) {
        401, 403 -> "La página del modelo pide iniciar sesión. Descárgalo desde el navegador " +
            "(huggingface.co/litert-community, busca gemma-4) y elígelo con Modelo → Elegir un archivo."
        404 -> "No encontré el archivo del modelo en su página. Puede que lo hayan movido; elígelo a mano con Modelo → Elegir un archivo."
        1006 -> "El teléfono se quedó sin espacio durante la descarga. Libera espacio e inténtalo de nuevo."
        REASON_TOO_SMALL -> "La descarga terminó, pero el archivo no está completo. Inténtalo de nuevo."
        else -> "La descarga falló (código $reason). Revisa tu Wi-Fi e inténtalo de nuevo."
    }

    fun noSpace(free: Long, needed: Long): String =
        "Necesito unos ${gb(needed)} GB libres y tu teléfono tiene ${gb(free)} GB. Libera espacio e inténtalo de nuevo."
}
