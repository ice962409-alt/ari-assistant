package com.ari.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Mantiene vivo el modo live cuando sales de Ari o cuando ella abre otra app por ti.
 *
 * Android solo deja usar el micrófono en segundo plano a un "servicio en primer plano"
 * con una notificación fija: así siempre sabes que Ari te está escuchando y puedes
 * terminar con un toque. El servicio no graba ni guarda audio; solo mantiene a Ari
 * despierta. Quien escucha y contesta sigue siendo la pantalla principal de Ari.
 */
class LiveSessionService : Service() {

    /** Lo que la notificación le pide a Ari. Lo registra MainActivity mientras el modo live está activo. */
    interface Controller {
        fun onTalkRequested()
        fun onEndRequested()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TALK -> {
                // Si el modo live ya terminó (notificación vieja), no se queda nada encendido.
                val live = controller
                if (live == null || !running) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                live.onTalkRequested()
            }
            ACTION_END -> {
                controller?.onEndRequested()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = build(this, intent?.getStringExtra(EXTRA_TEXT) ?: TEXT_LISTENING)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    } else {
                        startForeground(ID, notification)
                    }
                } catch (e: RuntimeException) {
                    // Android no lo permitió (por ejemplo, sin permiso de micrófono): el modo live
                    // sigue funcionando, pero solo con Ari en pantalla.
                    running = false
                    stopSelf()
                }
            }
        }
        // Si Android cierra el proceso, no se reinicia solo: el micrófono no se enciende sin ti.
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "modo_live"
        private const val ID = 5_000_000
        private const val ACTION_TALK = "com.ari.assistant.live.TALK"
        private const val ACTION_END = "com.ari.assistant.live.END"
        private const val EXTRA_TEXT = "texto"
        const val TEXT_LISTENING = "Te escucho aunque uses otras apps."

        @Volatile
        var controller: Controller? = null

        @Volatile
        var running = false

        /** Empieza mientras Ari está en pantalla (Android no deja encender el micrófono desde atrás). */
        fun start(context: Context) {
            running = true
            try {
                context.startForegroundService(Intent(context, LiveSessionService::class.java))
            } catch (e: RuntimeException) {
                running = false
            }
        }

        fun stop(context: Context) {
            if (!running) return
            running = false
            context.stopService(Intent(context, LiveSessionService::class.java))
        }

        /** Cambia el texto de la notificación ("Pensando…", "En pausa…"). */
        fun update(context: Context, text: String) {
            if (!running) return
            context.getSystemService(NotificationManager::class.java)?.notify(ID, build(context, text))
        }

        private fun build(context: Context, text: String): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Modo live", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Aparece mientras platicas con Ari y usas otras apps."
                    setShowBadge(false)
                }
            )
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
            )
            val talk = PendingIntent.getService(
                context, 11, Intent(context, LiveSessionService::class.java).setAction(ACTION_TALK), flags
            )
            val end = PendingIntent.getService(
                context, 12, Intent(context, LiveSessionService::class.java).setAction(ACTION_END), flags
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ari en modo live")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .addAction(Notification.Action.Builder(null, "Hablar", talk).build())
                .addAction(Notification.Action.Builder(null, "Terminar", end).build())
                .build()
        }
    }
}
