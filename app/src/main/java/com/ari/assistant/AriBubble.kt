package com.ari.assistant

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.ari.assistant.ui.AriOrbView
import kotlin.math.abs

/**
 * La burbuja de Ari: su carita flotando sobre las otras apps (en lugar del widget).
 * - Un toque: si estás en modo live, Ari te escucha; si no, abre Ari en modo live.
 * - Arrastrar: la mueves a donde quieras.
 * - Mantener presionada: se esconde (se vuelve a encender en Ajustes de Ari).
 *
 * Android solo deja dibujar sobre otras apps si tú das el permiso "Mostrar sobre otras
 * apps" en los ajustes del teléfono. Se apaga con un toque en su notificación.
 */
class AriBubbleService : Service() {

    private var orb: AriOrbView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE || !AriBubble.canShow(this)) {
            AriBubble.setEnabled(this, intent?.action != ACTION_HIDE && AriBubble.isEnabled(this))
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification())
            }
        } catch (e: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (orb == null) show()
        running = this
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        val manager = getSystemService(WindowManager::class.java) ?: return
        val size = (56 * resources.displayMetrics.density).toInt()
        val layout = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            x = prefs.getInt(KEY_X, resources.displayMetrics.widthPixels - size - 16)
            y = prefs.getInt(KEY_Y, resources.displayMetrics.heightPixels / 3)
        }
        val view = AriOrbView(this).apply { contentDescription = "Ari" }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val longPress = Runnable {
            if (!moved) {
                AriBubble.setEnabled(this, false)
                stopSelf()
            }
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = layout.x; startY = layout.y
                    moved = false
                    v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong() * 2)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (moved || abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        v.removeCallbacks(longPress)
                        layout.x = startX + dx.toInt()
                        layout.y = startY + dy.toInt()
                        runCatching { manager.updateViewLayout(v, layout) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (moved) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_X, layout.x).putInt(KEY_Y, layout.y).apply()
                    } else {
                        tapped()
                    }
                }
                MotionEvent.ACTION_CANCEL -> v.removeCallbacks(longPress)
            }
            true
        }
        runCatching { manager.addView(view, layout) }.onFailure {
            stopSelf()
            return
        }
        orb = view
        params = layout
    }

    /** Un toque: Ari te escucha (si ya está en modo live) o se abre en modo live. */
    private fun tapped() {
        val live = LiveSessionService.controller
        if (live != null && LiveSessionService.running) {
            live.onTalkRequested()
        } else {
            startActivity(Intent(this, LiveShortcutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun setMood(mood: AriOrbView.Mood) {
        orb?.mood = mood
    }

    override fun onDestroy() {
        if (running === this) running = null
        orb?.let { view -> runCatching { getSystemService(WindowManager::class.java)?.removeView(view) } }
        orb = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Burbuja de Ari", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Aparece mientras la burbuja de Ari está sobre tus apps."
                setShowBadge(false)
            }
        )
        val hide = PendingIntent.getService(
            this, 30, Intent(this, AriBubbleService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Burbuja de Ari")
            .setContentText("Tócala para hablar con Ari. Toca aquí para esconderla.")
            .setContentIntent(hide)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "burbuja"
        private const val ID = 7_000_000
        private const val ACTION_HIDE = "com.ari.assistant.burbuja.ESCONDER"
        private const val PREFS = "ari_burbuja"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        @Volatile
        var running: AriBubbleService? = null
    }
}

object AriBubble {
    private const val PREFS = "ari_burbuja"
    private const val KEY_ON = "activa"

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
    }

    /** Enciende la burbuja si la activaste y diste el permiso. Se llama al abrir y al salir de Ari. */
    fun start(context: Context) {
        if (!isEnabled(context) || !canShow(context)) return
        runCatching { context.startForegroundService(Intent(context, AriBubbleService::class.java)) }
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, AriBubbleService::class.java)) }
    }

    /** La pantalla de Android donde se da el permiso "Mostrar sobre otras apps" a Ari. */
    fun permissionIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** La carita de la burbuja copia el ánimo de Ari (escuchando, pensando, hablando). */
    fun mood(mood: AriOrbView.Mood) {
        AriBubbleService.running?.setMood(mood)
    }
}
