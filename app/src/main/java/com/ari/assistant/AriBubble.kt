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
 * - Un toque: se abre su tarjeta ("¿Cómo puedo ayudarte hoy?") con un campo para
 *   escribirle y los botones Escribir, Hablar y (solo en la Ari CEO) Claude. La X la cierra.
 * - Arrastrar: la mueves a donde quieras.
 * - Mantener presionada: se esconde (se vuelve a encender en Ajustes de Ari).
 *
 * Android solo deja dibujar sobre otras apps si tú das el permiso "Mostrar sobre otras
 * apps" en los ajustes del teléfono. Se apaga con un toque en su notificación.
 */
class AriBubbleService : Service() {

    private var orb: AriOrbView? = null
    private var params: WindowManager.LayoutParams? = null
    private var panel: android.view.View? = null
    private var panelOrb: AriOrbView? = null

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
                        openPanel()
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

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    /** La tarjeta de Ari (el boceto del dueño): carita, pregunta, campo para escribir y botones. */
    @SuppressLint("SetTextI18n")
    private fun openPanel() {
        if (panel != null) return
        val manager = getSystemService(WindowManager::class.java) ?: return
        orb?.visibility = android.view.View.GONE
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18f), dp(10f), dp(18f), dp(16f))
            background = com.ari.assistant.ui.AriTheme.rounded(this@AriBubbleService, com.ari.assistant.ui.AriTheme.surface, 24f, com.ari.assistant.ui.AriTheme.border)
            elevation = dp(8f).toFloat()
        }
        // La X para cerrar, arriba a la derecha.
        card.addView(android.widget.TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(com.ari.assistant.ui.AriTheme.muted)
            setPadding(dp(8f), dp(4f), dp(4f), dp(4f))
            setOnClickListener { closePanel() }
        }, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.END })
        val face = AriOrbView(this).apply { mood = orb?.mood ?: AriOrbView.Mood.IDLE }
        panelOrb = face
        card.addView(face, android.widget.LinearLayout.LayoutParams(dp(72f), dp(72f)))
        card.addView(android.widget.TextView(this).apply {
            text = "¿Cómo puedo ayudarte hoy?"
            textSize = 17f
            setTextColor(com.ari.assistant.ui.AriTheme.text)
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, dp(12f))
        })
        // Botones redondos: Escribir, Hablar y Claude (solo en la Ari CEO).
        val buttons = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun button(icon: String, label: String, action: () -> Unit) {
            val column = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8f), 0, dp(8f), 0)
                setOnClickListener { action() }
            }
            column.addView(android.widget.TextView(this).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(com.ari.assistant.ui.AriTheme.text)
                background = com.ari.assistant.ui.AriTheme.circle(com.ari.assistant.ui.AriTheme.bubble, com.ari.assistant.ui.AriTheme.ice, dp(1f))
            }, android.widget.LinearLayout.LayoutParams(dp(52f), dp(52f)))
            column.addView(android.widget.TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(com.ari.assistant.ui.AriTheme.muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(4f), 0, 0)
            })
            buttons.addView(column)
        }
        button("✎", "Escribir") { openAri(MainActivity.ACTION_WRITE, null) }
        button("🎙", "Hablar") { closePanel(); talk() }
        if (resources.getBoolean(R.bool.ari_ceo)) button("C.", "Claude") { openClaude() }
        card.addView(buttons)
        // Campo para escribirle sin abrir la app.
        val field = android.widget.EditText(this).apply {
            hint = "Escribe aquí…"
            textSize = 15f
            setTextColor(com.ari.assistant.ui.AriTheme.text)
            setHintTextColor(com.ari.assistant.ui.AriTheme.muted)
            background = com.ari.assistant.ui.AriTheme.rounded(this@AriBubbleService, com.ari.assistant.ui.AriTheme.bubble, 20f)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setSingleLine(true)
        }
        val send = {
            val text = field.text.toString().trim()
            if (text.isNotEmpty()) {
                field.text.clear()
                val ari = AriChat.controller
                if (ari != null) {
                    AriChat.youSaid(this, text)
                    ari.onReply(text)
                    closePanel()
                } else {
                    openAri(MainActivity.ACTION_WRITE, text)
                }
            }
        }
        field.setOnEditorActionListener { _, _, _ -> send(); true }
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14f), 0, 0)
        }
        row.addView(field, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(android.widget.TextView(this).apply {
            text = "↑"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(com.ari.assistant.ui.AriTheme.onIce)
            background = com.ari.assistant.ui.AriTheme.circle(com.ari.assistant.ui.AriTheme.ice)
            setOnClickListener { send() }
        }, android.widget.LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { leftMargin = dp(8f) })
        card.addView(row, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))

        val layout = WindowManager.LayoutParams(
            dp(300f), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Se puede escribir en ella; un toque fuera la cierra.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        card.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }
        runCatching { manager.addView(card, layout) }.onFailure {
            orb?.visibility = android.view.View.VISIBLE
            return
        }
        panel = card
    }

    private fun closePanel() {
        val view = panel ?: return
        panel = null
        panelOrb = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(view) }
        orb?.visibility = android.view.View.VISIBLE
    }

    /** Abre Ari: [action] ACTION_WRITE pone el teclado listo (con [text] si ya escribiste algo). */
    private fun openAri(action: String, text: String?) {
        closePanel()
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(action)
                .apply { if (text != null) putExtra(MainActivity.EXTRA_TEXT, text) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    /** Solo en la Ari CEO: abre Claude (la app si está instalada; si no, claude.ai). */
    private fun openClaude() {
        closePanel()
        val app = packageManager.getLaunchIntentForPackage("com.anthropic.claude")
        val intent = app ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/code"))
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Hablar: Ari te escucha (si ya está en modo live) o se abre en modo live. */
    private fun talk() {
        val live = LiveSessionService.controller
        if (live != null && LiveSessionService.running) {
            live.onTalkRequested()
        } else {
            startActivity(Intent(this, LiveShortcutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun setMood(mood: AriOrbView.Mood) {
        orb?.mood = mood
        panelOrb?.mood = mood
    }

    override fun onDestroy() {
        if (running === this) running = null
        closePanel()
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
