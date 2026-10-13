package com.ari.assistant

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONArray
import java.io.File

/**
 * Widget de Ari para la pantalla de inicio: rota mensajes animados con el
 * estado de Ari y da acceso directo a la app y al tablero de Claude, donde se
 * ve una barra con el progreso de lo que Claude está haciendo y sus pendientes.
 *
 * Todo lo que muestra sale del teléfono; el widget no se conecta a internet.
 * Por privacidad no muestra el texto de las conversaciones ni de los recuerdos,
 * porque cualquiera que vea la pantalla lo leería.
 */
class AriWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val views = buildViews(context)
        widgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /** El tablero "Pendientes de Ari" en claude.ai; se abre en el navegador. */
        private const val BOARD_URL = "https://claude.ai/artifact/7kCcA7Ppg1tGW1HDATrfyB"

        /** Vuelve a dibujar todos los widgets de Ari, por ejemplo al salir de la app. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, AriWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_ari)
            val modelReady = File(context.filesDir, "gemma-model.litertlm").let { it.exists() && it.length() > 0 }
            val memories = countMemories(context)
            val team = countTeam(context)

            views.setTextViewText(
                R.id.widget_status,
                if (modelReady) "Lista para ayudarte" else "Falta cargar el modelo"
            )
            views.setTextViewText(
                R.id.widget_line_1,
                if (modelReady) "Hola, soy Ari. ¿En qué trabajamos hoy?" else "Abre Ari y elige el modelo Gemma para empezar."
            )
            views.setTextViewText(
                R.id.widget_line_2,
                when (memories) {
                    0 -> "Aún no tengo recuerdos tuyos. Dime \"recuerda que…\""
                    1 -> "Guardo 1 recuerdo tuyo."
                    else -> "Guardo $memories recuerdos tuyos."
                }
            )
            views.setTextViewText(
                R.id.widget_line_3,
                when (team) {
                    0 -> "Todavía no tengo equipo. Dime \"busca modelos\" cuando tu laptop esté lista."
                    1 -> "Mi equipo: 1 modelo trabajando para la empresa."
                    else -> "Mi equipo: $team modelos trabajando para la empresa."
                }
            )
            views.setTextViewText(
                R.id.widget_line_4,
                "Toca \"Ver a Claude\" para ver en qué trabaja y si te necesita."
            )

            val openApp = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openAppIntent = PendingIntent.getActivity(
                context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_ari, openAppIntent)
            views.setOnClickPendingIntent(R.id.widget_flipper, openAppIntent)

            val openBoard = Intent(Intent.ACTION_VIEW, Uri.parse(BOARD_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widget_open_board,
                PendingIntent.getActivity(
                    context, 1, openBoard, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }

        private fun countMemories(context: Context): Int = try {
            AriDatabase(context).use { it.memories().size }
        } catch (_: Throwable) {
            0
        }

        /** Modelos contratados en todas las redes (el widget no sabe en qué red está). */
        private fun countTeam(context: Context): Int = try {
            context.getSharedPreferences("ari_agentes", Context.MODE_PRIVATE).all
                .filterKeys { it.startsWith("team|") }
                .values
                .sumOf { value -> (value as? String)?.let { JSONArray(it).length() } ?: 0 }
        } catch (_: Throwable) {
            0
        }
    }
}
