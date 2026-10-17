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
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import com.ari.assistant.voz.WakeSegmenter
import com.ari.assistant.voz.WakeWord
import com.ari.assistant.voz.WakeWordModel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Ari siempre atenta: escucha en segundo plano y, cuando dices "Ari" (sola, como al
 * llamar a alguien), se abre y te escucha.
 *
 * - Android solo deja usar el micrófono en segundo plano con una notificación fija:
 *   así siempre sabes que Ari está atenta y la apagas con un toque.
 * - Nada se graba ni sale del teléfono: cada palabra suelta se compara con tus 3
 *   muestras de "Ari" y se descarta al instante.
 * - Suelta el micrófono mientras Ari está en pantalla o en modo live (ahí ya te escucha).
 */
class WakeWordService : Service() {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) {
            AriWakeWord.setEnabled(this, false)
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(ID, notification())
            }
        } catch (e: RuntimeException) {
            // Android no lo permitió (por ejemplo, sin permiso de micrófono o desde atrás).
            stopSelf()
            return START_NOT_STICKY
        }
        val model = AriWakeWord.load(this)
        if (model == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) startListening(model)
        // Si Android cierra el proceso, intenta volver (si Android no lo deja, vuelve al abrir Ari).
        return START_STICKY
    }

    @SuppressLint("MissingPermission") // Solo se enciende con el permiso de micrófono concedido.
    private fun startListening(model: WakeWordModel) {
        running = true
        thread = Thread({ listen(model) }, "ari-nombre").apply { start() }
    }

    @SuppressLint("MissingPermission")
    private fun listen(model: WakeWordModel) {
        val rate = WakeWord.RATE
        val buffer = ShortArray(rate / 20)
        val segmenter = WakeSegmenter(rate)
        var record: AudioRecord? = null
        var quietUntil = 0L
        try {
            while (running) {
                try {
                    if (!AriWakeWord.micFree()) {
                        // Ari ya te escucha (en pantalla o en modo live): suelta el micrófono.
                        record?.let { runCatching { it.stop() }; it.release() }
                        record = null
                        segmenter.reset()
                        Thread.sleep(500)
                        continue
                    }
                    var active = record
                    if (active == null) {
                        active = openRecord(rate)
                        if (active == null) {
                            Thread.sleep(2_000)
                            continue
                        }
                        record = active
                    }
                    val read = active.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        // El micrófono se cayó (otra app, un error de Android): se vuelve a abrir.
                        runCatching { active.stop() }
                        active.release()
                        record = null
                        segmenter.reset()
                        Thread.sleep(500)
                        continue
                    }
                    if (read == 0) {
                        Thread.sleep(50)
                        continue
                    }
                    val word = segmenter.feed(buffer, read) ?: continue
                    if (SystemClock.elapsedRealtime() < quietUntil) continue
                    if (model.matches(word)) {
                        quietUntil = SystemClock.elapsedRealtime() + 3_000
                        // Suelta el micrófono para que Ari te escuche.
                        runCatching { active.stop() }
                        active.release()
                        record = null
                        segmenter.reset()
                        AriWakeWord.heardUntil = SystemClock.elapsedRealtime() + 4_000
                        onHeard()
                    }
                } catch (e: InterruptedException) {
                    throw e
                } catch (_: Exception) {
                    // Un error no deja sorda a Ari: suelta el micrófono y sigue escuchando.
                    record?.let { runCatching { it.stop() }; runCatching { it.release() } }
                    record = null
                    segmenter.reset()
                    Thread.sleep(1_000)
                }
            }
        } catch (_: InterruptedException) {
        } finally {
            record?.let { runCatching { it.stop() }; it.release() }
            // Si este hilo se terminó, el servicio debe poder arrancar otro.
            if (thread === Thread.currentThread()) running = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(rate: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val created = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate / 2)
            )
        }.getOrNull() ?: return null
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return null
        }
        return if (runCatching { created.startRecording() }.isSuccess) created else {
            created.release()
            null
        }
    }

    /** Me llamaste: un tonito y Ari se abre escuchándote (como el botón Hablar de la burbuja). */
    private fun onHeard() {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                Thread.sleep(200)
                release()
            }
        }
        val live = LiveSessionService.controller
        if (live != null && LiveSessionService.running) {
            android.os.Handler(mainLooper).post { live.onTalkRequested() }
            return
        }
        val open = Intent(this, LiveShortcutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(LiveShortcutActivity.EXTRA_BY_VOICE, true)
        // Desde atrás, Android solo deja abrir Ari si tiene el permiso de la burbuja
        // ("Mostrar sobre otras apps"). Sin él, queda un aviso para tocarlo.
        val opened = Settings.canDrawOverlays(this) && runCatching { startActivity(open) }.isSuccess
        if (!opened) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val tap = PendingIntent.getActivity(this, 41, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(
                ID + 1,
                Notification.Builder(this, CHANNEL_HEARD)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("¿Me llamaste?")
                    .setContentText("Toca aquí y te escucho.")
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setTimeoutAfter(30_000)
                    .build()
            )
        }
    }

    private fun stopListening() {
        running = false
        thread?.interrupt()
        thread = null
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ari atenta a su nombre", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Aparece mientras Ari espera a que digas \"Ari\"."
                setShowBadge(false)
            }
        )
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_HEARD, "Cuando dices \"Ari\"", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Si Ari no puede abrirse sola, te avisa aquí que la llamaste."
            }
        )
        val off = PendingIntent.getService(
            this, 40, Intent(this, WakeWordService::class.java).setAction(ACTION_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 42,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Di \"Ari\" para llamarme")
            .setContentText("Estoy atenta a mi nombre. No grabo nada.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Apagar", off).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "ari_nombre"
        private const val CHANNEL_HEARD = "ari_nombre_oido"
        private const val ID = 8_000_000
        private const val ACTION_OFF = "com.ari.assistant.nombre.APAGAR"
    }
}

/** Ajustes y muestras de "Ari siempre atenta". */
object AriWakeWord {
    private const val PREFS = "ari_nombre"
    private const val KEY_ON = "activa"
    private const val KEY_SENSITIVITY = "sensibilidad"
    private const val FILE = "ari_nombre.bin"

    /** true mientras Ari está en pantalla: ahí ya te escucha y el micrófono es suyo. */
    @Volatile
    var appVisible = false

    /** Un momento después de oír "Ari", para que la pantalla de Ari tome el micrófono. */
    @Volatile
    var heardUntil = 0L

    /** true con el modo live en pausa: ahí nadie usa el micrófono y "Ari" lo vuelve a despertar. */
    @Volatile
    var livePaused = false

    fun micFree() = !appVisible && (!LiveSessionService.running || livePaused) && SystemClock.elapsedRealtime() > heardUntil

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ON, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
    }

    fun hasModel(context: Context) = File(context.filesDir, FILE).exists()

    /** 1.0 normal; más alto la despierta más fácil (y con más equivocaciones). */
    fun sensitivity(context: Context) = prefs(context).getFloat(KEY_SENSITIVITY, 1.0f).toDouble()

    fun setSensitivity(context: Context, value: Double) {
        prefs(context).edit().putFloat(KEY_SENSITIVITY, value.toFloat()).apply()
    }

    /** Se enciende solo con Ari en pantalla (Android no deja encender el micrófono desde atrás). */
    fun start(context: Context) {
        if (!isEnabled(context) || !hasModel(context)) return
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        runCatching { context.startForegroundService(Intent(context, WakeWordService::class.java)) }
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, WakeWordService::class.java)) }
    }

    /** Guarda solo las formas del sonido (números), nunca la grabación. */
    fun save(context: Context, model: WakeWordModel) {
        val file = File(context.filesDir, FILE)
        val temp = File(context.filesDir, "$FILE.tmp")
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            out.writeInt(1)
            out.writeDouble(model.threshold)
            out.writeInt(model.templates.size)
            for (template in model.templates) {
                out.writeInt(template.size)
                out.writeInt(template.firstOrNull()?.size ?: 0)
                for (frame in template) for (value in frame) out.writeFloat(value)
            }
        }
        temp.renameTo(file)
    }

    /** El modelo guardado, con la sensibilidad que elegiste. */
    fun load(context: Context): WakeWordModel? = runCatching {
        DataInputStream(File(context.filesDir, FILE).inputStream().buffered()).use { input ->
            if (input.readInt() != 1) return null
            val threshold = input.readDouble()
            val templates = List(input.readInt()) {
                val frames = input.readInt()
                val dims = input.readInt()
                Array(frames) { FloatArray(dims) { input.readFloat() } }
            }
            if (templates.isEmpty()) return null
            val adjusted = (threshold * sensitivity(context))
                .coerceIn(WakeWordModel.MIN_THRESHOLD * 0.8, WakeWordModel.MAX_THRESHOLD * 1.2)
            WakeWordModel(templates, adjusted)
        }
    }.getOrNull()

    fun forget(context: Context) {
        File(context.filesDir, FILE).delete()
        setEnabled(context, false)
        stop(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
