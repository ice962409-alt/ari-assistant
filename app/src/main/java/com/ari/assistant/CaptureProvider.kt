package com.ari.assistant

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * La "ventanilla" por la que la app de cámara le entrega a Ari la foto que tomas.
 *
 * La cámara es otra app y no puede escribir en las carpetas privadas de Ari. Ari le
 * presta un solo archivo con permiso temporal (solo para esa foto); nadie más puede
 * abrir esta ventanilla, porque no está publicada para otras apps.
 */
class CaptureProvider : ContentProvider() {

    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val context = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        // Solo nombres simples dentro de la carpeta de fotos: nada de "../".
        if (!NAME.matches(name)) throw FileNotFoundException()
        return File(folder(context), name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val target = file(uri)
        val flags = if (mode.contains('w')) {
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(target, flags)
    }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val target = file(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>("Foto.jpg", target.length()))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        private const val AUTHORITY = "com.ari.assistant.camara"
        private val NAME = Regex("foto-[0-9a-f-]+\\.jpg")

        private fun folder(context: Context) = File(context.cacheDir, "camara").apply { mkdirs() }

        /** Un archivo vacío para la próxima foto y la dirección que se le presta a la cámara. */
        fun newPhoto(context: Context): Pair<File, Uri> {
            val name = "foto-${java.util.UUID.randomUUID()}.jpg"
            return File(folder(context), name) to Uri.parse("content://$AUTHORITY/$name")
        }

        fun clean(context: Context) {
            folder(context).listFiles()?.forEach { it.delete() }
        }
    }
}
