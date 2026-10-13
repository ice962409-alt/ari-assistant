package com.ari.estado

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.time.Duration
import java.time.Instant

/** Widget "Claude trabajando": la tarea, la barra de progreso y si te necesita. */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        renderAll(context)
        RefreshJob.schedule(context)
        RefreshJob.runNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            renderAll(context, refreshing = true)
            RefreshJob.runNow(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.ari.estado.REFRESH"
        private const val BOARD_URL = "https://claude.ai/artifact/7kCcA7Ppg1tGW1HDATrfyB"

        fun renderAll(context: Context, refreshing: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, refreshing)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context, refreshing: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_estado)
            val status = StatusStore.status(context)
            val error = StatusStore.error(context)
            val green = context.getColor(R.color.ice)
            val orange = context.getColor(R.color.orange)

            when {
                !TokenVault.hasToken(context) -> {
                    views.setTextViewText(R.id.widget_state, "Sin configurar")
                    views.setTextColor(R.id.widget_state, orange)
                    views.setTextViewText(R.id.widget_task, "Toca aquí para poner la llave de solo lectura.")
                    views.setProgressBar(R.id.widget_progress, 100, 0, false)
                    views.setViewVisibility(R.id.widget_activity, View.GONE)
                    views.setTextViewText(R.id.widget_meta, "")
                    views.setTextViewText(R.id.widget_note, "")
                }

                status == null -> {
                    views.setTextViewText(R.id.widget_state, if (refreshing) "Consultando…" else "Sin datos")
                    views.setTextColor(R.id.widget_state, green)
                    views.setTextViewText(R.id.widget_task, "Todavía no he podido leer el estado de Claude.")
                    views.setProgressBar(R.id.widget_progress, 100, 0, false)
                    views.setViewVisibility(R.id.widget_activity, if (refreshing) View.VISIBLE else View.GONE)
                    views.setTextViewText(R.id.widget_meta, "")
                    views.setTextViewText(R.id.widget_note, error.orEmpty())
                }

                else -> {
                    val stale = status.isStale()
                    val working = status.active && !stale
                    views.setTextViewText(
                        R.id.widget_state,
                        when {
                            refreshing -> "Consultando…"
                            stale -> "Sin noticias"
                            working -> "Trabajando"
                            else -> "En pausa"
                        }
                    )
                    views.setTextColor(R.id.widget_state, if (working) green else orange)
                    views.setTextViewText(R.id.widget_task, status.task.ifBlank { "Sin tarea registrada." })
                    views.setProgressBar(R.id.widget_progress, 100, status.progress, false)
                    // La barra fina animada solo se ve mientras Claude trabaja.
                    views.setViewVisibility(R.id.widget_activity, if (working || refreshing) View.VISIBLE else View.GONE)

                    val step = if (status.step > 0 && status.steps > 0) "Paso ${status.step} de ${status.steps} · " else ""
                    val pending = when (status.pending) {
                        0 -> ""
                        1 -> " · 1 pendiente"
                        else -> " · ${status.pending} pendientes"
                    }
                    views.setTextViewText(R.id.widget_meta, "$step${status.progress}% · ${ago(status.updatedAt)}$pending")
                    views.setTextViewText(
                        R.id.widget_note,
                        when {
                            error != null -> error
                            stale -> "Quizá se cerró la sesión de Claude. Toca aquí y escribe \"sigue\"."
                            !status.active -> "Claude se detuvo. Toca aquí y escribe \"sigue\"."
                            else -> ""
                        }
                    )
                }
            }

            val openSetup = PendingIntent.getActivity(
                context, 0,
                Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_task, openSetup)

            val refresh = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, StatusWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refresh)

            val board = PendingIntent.getActivity(
                context, 2,
                Intent(Intent.ACTION_VIEW, Uri.parse(BOARD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_board, board)

            // Tocar el aviso naranja abre la sesión de Claude para escribir "sigue".
            status?.session?.takeIf { it.isNotBlank() }?.let { url ->
                views.setOnClickPendingIntent(
                    R.id.widget_note,
                    PendingIntent.getActivity(
                        context, 3,
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            return views
        }

        private fun ago(time: Instant?): String {
            if (time == null) return "sin hora"
            val minutes = Duration.between(time, Instant.now()).toMinutes().coerceAtLeast(0)
            return when {
                minutes < 1 -> "ahora"
                minutes < 60 -> "hace $minutes min"
                minutes < 24 * 60 -> "hace ${minutes / 60} h"
                else -> "hace ${minutes / (24 * 60)} d"
            }
        }
    }
}
