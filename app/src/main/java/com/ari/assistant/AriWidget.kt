package com.ari.assistant

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_POKE) return
        // Le "hiciste cosquillas": Ari pone cara feliz y saluda unos segundos.
        val pending = goAsync()
        render(context, happy = true)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                render(context, happy = false)
            } finally {
                pending.finish()
            }
        }, HAPPY_MS)
    }

    companion object {
        private const val ACTION_POKE = "com.ari.assistant.WIDGET_POKE"
        private const val HAPPY_MS = 4000L

        private val GREETINGS = listOf(
            "¡Hola! Toca \"Hablar\" y platicamos.",
            "Jaja, ¡me hiciste cosquillas!",
            "Aquí estoy, lista para lo que necesites.",
            "¿Me llamaste? Te escucho.",
            "¡Qué gusto verte por aquí!"
        )
        private val FACES = listOf(
            R.id.widget_face_1, R.id.widget_face_2, R.id.widget_face_3, R.id.widget_face_4, R.id.widget_face_5
        )
        private val NORMAL_FACES = listOf(
            R.drawable.ari_face_open, R.drawable.ari_face_glow, R.drawable.ari_face_open,
            R.drawable.ari_face_glow, R.drawable.ari_face_blink
        )

        private fun render(context: Context, happy: Boolean) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, AriWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, happy)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
        /** El tablero "Pendientes de Ari" en claude.ai; se abre en el navegador. */
        private const val BOARD_URL = "https://claude.ai/artifact/7kCcA7Ppg1tGW1HDATrfyB"

        /** Vuelve a dibujar todos los widgets de Ari, por ejemplo al salir de la app. */
        fun refresh(context: Context) = render(context, happy = false)

        private fun buildViews(context: Context, happy: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_ari)
            // El modelo elegido a mano o el descargado desde la app.
            val modelReady = File(context.filesDir, "gemma-model.litertlm").let { it.exists() && it.length() > 0 } ||
                runCatching { ModelDownload(context).downloadedFile() != null }.getOrDefault(false)
            FACES.forEachIndexed { index, id ->
                views.setImageViewResource(id, if (happy) R.drawable.ari_face_happy else NORMAL_FACES[index])
            }
            val memories = countMemories(context)
            val team = countTeam(context)

            views.setTextViewText(
                R.id.widget_status,
                when {
                    happy -> "Feliz"
                    modelReady -> "Lista para platicar"
                    else -> "Falta el modelo"
                }
            )
            views.setTextViewText(
                R.id.widget_line_1,
                if (modelReady) "Hola, soy Ari. ¿En qué trabajamos hoy?" else "Ábreme y toca Modelo para descargar a Gemma."
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
                "Toca mi cara o \"Hablar\" para platicar conmigo en voz."
            )

            // Al tocarla, el saludo ocupa todos los mensajes, para que se vea sea cual sea el que está en pantalla.
            if (happy) {
                val greeting = GREETINGS.random()
                listOf(R.id.widget_line_1, R.id.widget_line_2, R.id.widget_line_3, R.id.widget_line_4)
                    .forEach { views.setTextViewText(it, greeting) }
            }

            val openApp = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val openAppIntent = PendingIntent.getActivity(
                context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_ari, openAppIntent)

            // Su cara y "Hablar" abren Ari directo en el modo live.
            val live = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_LIVE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val liveIntent = PendingIntent.getActivity(
                context, 2, live, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_live, liveIntent)
            views.setOnClickPendingIntent(R.id.widget_face, liveIntent)

            val poke = PendingIntent.getBroadcast(
                context, 3,
                Intent(context, AriWidget::class.java).setAction(ACTION_POKE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_flipper, poke)

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
