package com.ari.assistant

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ari.assistant.agents.normalizeForAgents
import java.time.LocalDate
import java.time.ZoneId

/**
 * Solo en la Ari CEO: tus fotos y archivos.
 * - "muéstrame las fotos de hoy / ayer / esta semana", "mi última foto", "mi última captura".
 * - "busca el archivo factura": documentos por su nombre.
 * Nada sale del teléfono: Ari solo te muestra lo que encuentra. Las frases no usan
 * Android, para poder probarlas; [photos] y [files] buscan en el teléfono.
 */
object AriMedia {

    enum class When { TODAY, YESTERDAY, WEEK }

    sealed class Request {
        data class Photos(val range: When) : Request()
        object LastPhoto : Request()
        object LastScreenshot : Request()
        data class Files(val name: String) : Request()
    }

    data class Found(val uri: Uri, val name: String, val mime: String)

    private const val SHOW = "(?:muestrame|ensename|ponme|abre|dame|busca|buscame|quiero ver|ver)"
    private val PHOTOS = Regex("^(?:$SHOW\\s+)?(?:mis\\s+|las\\s+)?fotos\\s+(?:de\\s+)?(hoy|ayer|esta semana|la semana)$")
    private val LAST_PHOTO = Regex("^(?:$SHOW\\s+|cual es\\s+)?(?:mi\\s+|la\\s+)?ultima foto$")
    private val LAST_SHOT = Regex("^(?:$SHOW\\s+|cual es\\s+)?(?:mi\\s+|la\\s+)?ultima captura(?:\\s+de\\s+pantalla)?$")
    private val FILES = Regex("^(?:busca|buscame|encuentra|encuentrame|donde esta|donde quedo)\\s+(?:el\\s+|mi\\s+|un\\s+)?(?:archivo|documento|pdf)\\s+(?:de\\s+|que se llama\\s+|llamado\\s+)?(.+)$")

    fun parse(text: String): Request? {
        val t = normalizeForAgents(text)
        PHOTOS.matchEntire(t)?.let { m ->
            return Request.Photos(
                when (m.groupValues[1]) {
                    "hoy" -> When.TODAY
                    "ayer" -> When.YESTERDAY
                    else -> When.WEEK
                }
            )
        }
        if (LAST_PHOTO.matches(t)) return Request.LastPhoto
        if (LAST_SHOT.matches(t)) return Request.LastScreenshot
        FILES.matchEntire(t)?.let { m ->
            val name = m.groupValues[1].trim()
            return if (name.length >= 2) Request.Files(name) else null
        }
        return null
    }

    /** El rango de fechas (en milisegundos) de [range], contando desde [today]. */
    fun millisRange(range: When, today: LocalDate, zone: ZoneId): LongRange {
        fun start(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (range) {
            When.TODAY -> start(today) until start(today.plusDays(1))
            When.YESTERDAY -> start(today.minusDays(1)) until start(today)
            When.WEEK -> start(today.minusDays(6)) until start(today.plusDays(1))
        }
    }

    // --- En el teléfono ---

    fun photoPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canSeePhotos(context: Context) = context.checkSelfPermission(photoPermission()) == PackageManager.PERMISSION_GRANTED

    /** Android 11+: buscar documentos en cualquier carpeta pide "Acceso a todos los archivos". */
    fun canSeeFiles(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else canSeePhotos(context)

    fun filesPermissionIntent(context: Context) =
        android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    fun photos(context: Context, request: Request, max: Int = 12): List<Found> {
        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = StringBuilder()
        val args = mutableListOf<String>()
        when (request) {
            is Request.Photos -> {
                val range = millisRange(request.range, LocalDate.now(), ZoneId.systemDefault())
                // DATE_ADDED va en segundos.
                selection.append("${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?")
                args += (range.first / 1000).toString()
                args += ((range.last + 1) / 1000).toString()
            }
            Request.LastScreenshot -> {
                selection.append("(${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)")
                args += "%Screenshot%"
                args += "Screenshot%"
            }
            else -> Unit
        }
        val limit = if (request is Request.Photos) max else 1
        val out = mutableListOf<Found>()
        context.contentResolver.query(
            images,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE),
            selection.toString().ifEmpty { null },
            args.toTypedArray().takeIf { it.isNotEmpty() },
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < limit) {
                out += Found(
                    ContentUris.withAppendedId(images, cursor.getLong(0)),
                    cursor.getString(1).orEmpty(),
                    cursor.getString(2) ?: "image/*"
                )
            }
        }
        return out
    }

    fun files(context: Context, name: String, max: Int = 8): List<Found> {
        val collection = MediaStore.Files.getContentUri("external")
        val words = AppControl.simplify(name).split(' ').filter { it.length >= 2 }.take(4)
        if (words.isEmpty()) return emptyList()
        val selection = words.joinToString(" AND ") { "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" } +
            " AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"
        val out = mutableListOf<Found>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MIME_TYPE),
            selection,
            words.map { "%$it%" }.toTypedArray(),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < max) {
                out += Found(
                    ContentUris.withAppendedId(collection, cursor.getLong(0)),
                    cursor.getString(1).orEmpty(),
                    cursor.getString(2) ?: "*/*"
                )
            }
        }
        return out
    }

    /** Abre la foto o el archivo en la app que sepa mostrarlo. */
    fun open(context: Context, found: Found) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(found.uri, found.mime)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
