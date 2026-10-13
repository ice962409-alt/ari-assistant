package com.ari.estado

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Guarda el último cierre inesperado para mostrarlo en la pantalla de la app.
 * Así, si algo falla en el teléfono, se puede ver qué pasó sin herramientas
 * de desarrollador.
 */
class AriEstadoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                CrashLog.save(this, error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

object CrashLog {
    private const val PREFS = "ari_estado_crash"

    fun save(context: Context, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("trace", trace.take(4000))
            .putLong("at", System.currentTimeMillis())
            .commit()
    }

    fun last(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("trace", null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
