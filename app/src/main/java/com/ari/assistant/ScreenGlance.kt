package com.ari.assistant

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import java.io.File

/**
 * "Mira mi pantalla": Ari toma UNA captura de tu pantalla cuando se lo pides.
 *
 * Android siempre pregunta antes de dejar que una app vea la pantalla, y lo vuelve a
 * preguntar cada vez: Ari nunca la ve sin que tú toques "Permitir". Toma una sola
 * imagen, deja de mirar enseguida y la imagen se borra en cuanto Gemma contesta.
 * Nada sale del teléfono.
 */
object ScreenGlance {

    /** Quien espera la captura (MainActivity). null si Ari ya no está abierta. */
    interface Listener {
        /** [file] es la captura, o null si no diste permiso o falló. */
        fun onScreen(file: File?, message: String?)
    }

    @Volatile
    var listener: Listener? = null

    private const val CHANNEL = "ver_pantalla"
    private const val ASK_ID = 6_000_000

    /** Con Ari fuera de la pantalla, Android no la deja abrir la pregunta sola: te deja un aviso para tocar. */
    fun askWithNotification(context: Context) {
        if (!AlarmReminders.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ver tu pantalla", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cuando le pides a Ari que mire tu pantalla, tocas aquí para dejarla."
                setSound(null, null)
            }
        )
        val open = PendingIntent.getActivity(
            context, 20,
            Intent(context, ScreenConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            ASK_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ari quiere ver tu pantalla")
                .setContentText("Toca aquí y luego \"Permitir\". Solo toma una imagen.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setTimeoutAfter(ASK_VALID_MS)
                .build()
        )
    }

    fun cancelAsk(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ASK_ID)
    }

    /** Cuánto tiempo sigue en pie el "mira mi pantalla". */
    const val ASK_VALID_MS = 2 * 60_000L

    internal fun deliver(file: File?, message: String?) {
        Handler(Looper.getMainLooper()).post {
            val target = listener
            if (target == null) file?.delete() else target.onScreen(file, message)
        }
    }
}

/** Muestra la pregunta de Android ("¿Permitir que Ari vea tu pantalla?") y le pasa la respuesta a la captura. */
class ScreenConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenGlance.cancelAsk(this)
        if (savedInstanceState != null) return
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            ScreenGlance.deliver(null, "Este teléfono no me deja ver la pantalla.")
            finish()
            return
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Toda la pantalla, tal como la ves.
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST)
        } catch (e: RuntimeException) {
            ScreenGlance.deliver(null, "Este teléfono no me deja ver la pantalla.")
            finish()
        }
    }

    @Deprecated("Uses the compatible activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST) return
        if (resultCode != RESULT_OK || data == null) {
            ScreenGlance.deliver(null, "Está bien, no miro tu pantalla.")
        } else {
            // El servicio arranca mientras esta pantalla sigue al frente: Android lo exige.
            val start = Intent(this, ScreenGlanceService::class.java)
                .putExtra(ScreenGlanceService.EXTRA_CODE, resultCode)
                .putExtra(ScreenGlanceService.EXTRA_DATA, data)
            try {
                startForegroundService(start)
            } catch (e: RuntimeException) {
                ScreenGlance.deliver(null, "No pude mirar tu pantalla: ${e.message ?: "Android no lo permitió"}.")
            }
        }
        finish()
        // Sin animación: la captura sale limpia, sin esta pantalla encima.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val REQUEST = 1
    }
}

/** Toma una imagen de la pantalla y se apaga. Android exige este servicio con su notificación para ver la pantalla. */
class ScreenGlanceService : Service() {

    private var thread: HandlerThread? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    @Volatile
    private var latest: Image? = null
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (thread != null) return START_NOT_STICKY
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(ID, notification())
            }
        } catch (e: RuntimeException) {
            ScreenGlance.deliver(null, "No pude mirar tu pantalla: Android no lo permitió.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (data == null) {
            fail("No pude mirar tu pantalla.")
            return START_NOT_STICKY
        }
        val worker = HandlerThread("ari-pantalla").also { it.start() }
        thread = worker
        val handler = Handler(worker.looper)
        handler.post { capture(code, data, handler) }
        return START_NOT_STICKY
    }

    private fun capture(code: Int, data: Intent, handler: Handler) {
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(code, data)
            if (projection == null) {
                fail("No pude mirar tu pantalla.")
                return
            }
            this.projection = projection
            // Android 14 pide avisar antes de empezar; si el sistema la corta, Ari termina.
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { if (!finished) finish() }
                }
            }, handler)

            val metrics = DisplayMetrics()
            val screen = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            @Suppress("DEPRECATION")
            screen.getRealMetrics(metrics)
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels))
            val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
            val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            this.reader = reader
            // Se queda siempre con la imagen más nueva: la pantalla solo manda una cuando cambia.
            reader.setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (e: IllegalStateException) { null }
                if (image != null) {
                    latest?.close()
                    latest = image
                }
            }, handler)
            display = projection.createVirtualDisplay(
                "Ari mira tu pantalla", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler
            )
            val start = SystemClock.uptimeMillis()
            // Espera a que se cierren la pregunta de Android y la barra de notificaciones.
            val take = object : Runnable {
                override fun run() {
                    if (finished) return
                    val image = latest
                    if (image == null && SystemClock.uptimeMillis() - start < GIVE_UP_MS) {
                        handler.postDelayed(this, 200)
                        return
                    }
                    latest = null
                    if (image == null) {
                        fail("No pude ver la pantalla a tiempo.")
                        return
                    }
                    val file = try { save(image, width, height) } catch (e: Throwable) { null } finally { image.close() }
                    finish()
                    ScreenGlance.deliver(file, if (file == null) "No pude guardar lo que vi." else null)
                }
            }
            handler.postDelayed(take, SETTLE_MS)
        } catch (e: Throwable) {
            fail("No pude mirar tu pantalla: ${e.message ?: e.javaClass.simpleName}.")
        }
    }

    private fun save(image: Image, width: Int, height: Int): File {
        val plane = image.planes[0]
        val rowWidth = plane.rowStride / plane.pixelStride
        val padded = Bitmap.createBitmap(rowWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val bitmap = if (rowWidth == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        return AttachmentReader.save(this, bitmap)
    }

    private fun fail(message: String) {
        finish()
        ScreenGlance.deliver(null, message)
    }

    /** Deja de mirar la pantalla en cuanto tiene la imagen. */
    private fun finish() {
        finished = true
        latest?.close()
        latest = null
        runCatching { display?.release() }
        display = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.stop() }
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!finished) finish()
        thread?.quitSafely()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Mirando tu pantalla", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aparece los segundos en que Ari toma la imagen de tu pantalla."
                setShowBadge(false)
            }
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Ari está mirando tu pantalla")
            .setContentText("Solo una imagen; se borra al contestarte.")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_CODE = "codigo"
        const val EXTRA_DATA = "permiso"
        private const val CHANNEL = "mirando_pantalla"
        private const val ID = 6_000_001
        private const val MAX_SIDE = 1024
        private const val SETTLE_MS = 900L
        private const val GIVE_UP_MS = 4000L
    }
}
