package com.ari.estado

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Consulta el estado de Claude en segundo plano: cada 15 minutos (lo mínimo
 * que permite Android) y al tocar "Actualizar". Después redibuja el widget y,
 * si Claude se detuvo o dejó de dar noticias, avisa con una notificación.
 */
class RefreshJob : JobService() {

    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        worker = Thread {
            try {
                refresh(applicationContext)
            } catch (e: Throwable) {
                CrashLog.save(applicationContext, e)
                StatusStore.saveError(applicationContext, "Error al revisar: ${e.message ?: e.javaClass.simpleName}")
            }
            jobFinished(params, false)
        }.also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        return true
    }

    companion object {
        private const val PERIODIC_ID = 1
        private const val NOW_ID = 2
        private const val PERIOD_MS = 15L * 60 * 1000
        private const val CHANNEL = "claude"
        private const val BOARD_URL = "https://claude.ai/artifact/7kCcA7Ppg1tGW1HDATrfyB"

        /** Programa la revisión cada 15 minutos (no duplica si ya existe). */
        fun schedule(context: Context) = safely(context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return@safely
            if (scheduler.getPendingJob(PERIODIC_ID) != null) return@safely
            scheduler.schedule(
                JobInfo.Builder(PERIODIC_ID, ComponentName(context, RefreshJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build()
            )
        }

        /** Programar nunca debe cerrar la app: si Android lo rechaza, se guarda el motivo. */
        private fun safely(context: Context, block: () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                CrashLog.save(context, e)
                StatusStore.saveError(context, "No pude programar la revisión: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        /** Revisa en cuanto haya red. */
        fun runNow(context: Context) = safely(context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return@safely
            scheduler.schedule(
                JobInfo.Builder(NOW_ID, ComponentName(context, RefreshJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(5_000)
                    .build()
            )
        }

        fun cancelAll(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancelAll()
        }

        /** Consulta, guarda y redibuja. Se llama fuera del hilo principal. */
        fun refresh(context: Context) {
            val token = TokenVault.load(context)
            if (token == null) {
                StatusStore.saveError(context, "Configura la llave en la app Ari Estado.")
            } else {
                try {
                    StatusStore.saveStatus(context, StatusFetcher.fetch(token))
                } catch (e: Exception) {
                    StatusStore.saveError(context, e.message ?: "No se pudo leer el estado.")
                }
            }
            StatusWidget.renderAll(context)
            StatusStore.status(context)?.let { alertIfNeeded(context, it) }
        }

        /** Avisa una sola vez por cada pausa o silencio de Claude. */
        private fun alertIfNeeded(context: Context, status: ClaudeStatus) {
            val (key, text) = when {
                status.isStale() ->
                    "stale:${status.updatedAt}" to
                        "Claude no da noticias hace rato; quizá se cerró la sesión. Entra y escribe \"sigue\"."
                !status.active ->
                    "paused:${status.updatedAt}" to
                        "Claude se detuvo: ${status.task.ifBlank { "terminó su tarea" }}. Escribe \"sigue\" para que continúe."
                else -> {
                    StatusStore.setLastAlert(context, null)
                    return
                }
            }
            if (StatusStore.lastAlert(context) == key) return
            StatusStore.setLastAlert(context, key)
            notify(context, text)
        }

        private fun notify(context: Context, text: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Avisos de Claude", NotificationManager.IMPORTANCE_DEFAULT)
            )
            // Lleva directo a la sesión de Claude Code, donde se escribe "sigue".
            val target = StatusStore.status(context)?.session?.takeIf { it.isNotBlank() } ?: BOARD_URL
            val openBoard = PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Ari · Claude")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(openBoard)
                .setAutoCancel(true)
                .build()
            manager.notify(1, notification)
        }
    }
}
