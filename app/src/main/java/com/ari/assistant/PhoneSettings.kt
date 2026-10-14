package com.ari.assistant

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.ari.assistant.agents.normalizeForAgents

/**
 * Solo en la Ari CEO: ajustes del teléfono por voz, sin abrir otras apps.
 * Linterna, volumen, música (pausa, siguiente), no molestar y brillo.
 * La lógica de las frases no usa Android, para poder probarla; [run] hace el trabajo.
 */
object PhoneSettings {

    enum class Volume { UP, DOWN, MAX, MUTE, UNMUTE }
    enum class Media { PLAY_PAUSE, PAUSE, PLAY, NEXT, PREVIOUS }
    enum class Brightness { UP, DOWN, MAX, MIN }

    sealed class Command {
        data class Torch(val on: Boolean) : Command()
        data class SetVolume(val change: Volume) : Command()
        data class Music(val key: Media) : Command()
        data class DoNotDisturb(val on: Boolean) : Command()
        data class SetBrightness(val change: Brightness) : Command()
    }

    private val TORCH_ON = Regex("^(?:prende|enciende|activa|pon|prendeme|abre)\\s+(?:la\\s+)?(?:linterna|lampara|luz del telefono|flash)$")
    private val TORCH_OFF = Regex("^(?:apaga|desactiva|quita|cierra)\\s+(?:la\\s+)?(?:linterna|lampara|luz del telefono|flash)$")
    private val VOL_UP = Regex("^(?:sube|subele|aumenta)(?:\\s+(?:el|al))?\\s+volumen(?:\\s+(?:un poco|mas))?$|^mas volumen$")
    private val VOL_DOWN = Regex("^(?:baja|bajale|disminuye)(?:\\s+(?:el|al))?\\s+volumen(?:\\s+(?:un poco|mas))?$|^menos volumen$")
    private val VOL_MAX = Regex("^(?:(?:pon|sube)\\s+(?:el\\s+)?)?volumen\\s+al\\s+maximo$|^(?:sube|pon)\\s+(?:el\\s+)?volumen\\s+a\\s+todo$")
    private val MUTE = Regex("^(?:silencia|mutea)(?:\\s+(?:el\\s+)?(?:telefono|celular|volumen|sonido))?$|^pon(?:\\s+el\\s+(?:telefono|celular))?\\s+en\\s+silencio$")
    private val UNMUTE = Regex("^(?:quita|quitale)\\s+(?:el\\s+)?silencio$|^activa\\s+(?:el\\s+)?sonido$")
    private val PAUSE = Regex("^(?:pausa|pausala|pausar|deten|detente|para)\\s+(?:la\\s+)?(?:musica|cancion|video|reproduccion)$|^pausa$")
    private val PLAY = Regex("^(?:sigue|continua|reanuda|dale play a)\\s+(?:con\\s+)?(?:la\\s+)?(?:musica|cancion|reproduccion|video)$|^play$")
    private val NEXT = Regex("^(?:siguiente|pasa la|salta la|cambia la|cambia de)\\s+(?:cancion|rola)$|^cancion siguiente$|^pon la siguiente(?:\\s+cancion)?$")
    private val PREVIOUS = Regex("^(?:cancion\\s+anterior|anterior cancion|regresa la cancion|pon la anterior|la anterior)$")
    private val DND_ON = Regex("^(?:activa|pon|prende|enciende)\\s+(?:el\\s+|modo\\s+)?(?:no molestar|modo no molestar)$|^no me molesten$")
    private val DND_OFF = Regex("^(?:quita|desactiva|apaga)\\s+(?:el\\s+|modo\\s+)?(?:no molestar|modo no molestar)$")
    private val BRIGHT_UP = Regex("^(?:sube|subele|aumenta)(?:\\s+el)?\\s+brillo(?:\\s+(?:un poco|mas))?$|^mas brillo$")
    private val BRIGHT_DOWN = Regex("^(?:baja|bajale|disminuye)(?:\\s+el)?\\s+brillo(?:\\s+(?:un poco|mas))?$|^menos brillo$")
    private val BRIGHT_MAX = Regex("^(?:(?:pon|sube)\\s+(?:el\\s+)?)?brillo\\s+al\\s+maximo$")
    private val BRIGHT_MIN = Regex("^(?:(?:pon|baja)\\s+(?:el\\s+)?)?brillo\\s+al\\s+minimo$")

    fun parse(text: String): Command? {
        val t = normalizeForAgents(text)
        return when {
            TORCH_ON.matches(t) -> Command.Torch(true)
            TORCH_OFF.matches(t) -> Command.Torch(false)
            VOL_MAX.matches(t) -> Command.SetVolume(Volume.MAX)
            VOL_UP.matches(t) -> Command.SetVolume(Volume.UP)
            VOL_DOWN.matches(t) -> Command.SetVolume(Volume.DOWN)
            UNMUTE.matches(t) -> Command.SetVolume(Volume.UNMUTE)
            MUTE.matches(t) -> Command.SetVolume(Volume.MUTE)
            PAUSE.matches(t) -> Command.Music(Media.PAUSE)
            PLAY.matches(t) -> Command.Music(Media.PLAY)
            PREVIOUS.matches(t) -> Command.Music(Media.PREVIOUS)
            NEXT.matches(t) -> Command.Music(Media.NEXT)
            DND_ON.matches(t) -> Command.DoNotDisturb(true)
            DND_OFF.matches(t) -> Command.DoNotDisturb(false)
            BRIGHT_MAX.matches(t) -> Command.SetBrightness(Brightness.MAX)
            BRIGHT_MIN.matches(t) -> Command.SetBrightness(Brightness.MIN)
            BRIGHT_UP.matches(t) -> Command.SetBrightness(Brightness.UP)
            BRIGHT_DOWN.matches(t) -> Command.SetBrightness(Brightness.DOWN)
            else -> null
        }
    }

    /** El brillo nuevo (0 a 255) a partir del actual. */
    fun nextBrightness(current: Int, change: Brightness): Int = when (change) {
        Brightness.UP -> (current + 64).coerceAtMost(255)
        Brightness.DOWN -> (current - 64).coerceAtLeast(10)
        Brightness.MAX -> 255
        Brightness.MIN -> 10
    }

    /** Lo que Ari contesta. [needs] trae la pantalla de permiso si falta uno. */
    data class Outcome(val message: String, val needs: Intent? = null)

    fun run(context: Context, command: Command): Outcome = try {
        when (command) {
            is Command.Torch -> torch(context, command.on)
            is Command.SetVolume -> volume(context, command.change)
            is Command.Music -> media(context, command.key)
            is Command.DoNotDisturb -> doNotDisturb(context, command.on)
            is Command.SetBrightness -> brightness(context, command.change)
        }
    } catch (e: Exception) {
        Outcome("No pude hacerlo en este teléfono.")
    }

    private fun torch(context: Context, on: Boolean): Outcome {
        val cameras = context.getSystemService(CameraManager::class.java) ?: return Outcome("Este teléfono no me deja usar la linterna.")
        val id = cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return Outcome("Este teléfono no tiene linterna.")
        cameras.setTorchMode(id, on)
        return Outcome(if (on) "Listo, prendí la linterna." else "Listo, apagué la linterna.")
    }

    private fun volume(context: Context, change: Volume): Outcome {
        val audio = context.getSystemService(AudioManager::class.java) ?: return Outcome("No pude cambiar el volumen.")
        val stream = AudioManager.STREAM_MUSIC
        when (change) {
            Volume.UP -> repeat(2) { audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
            Volume.DOWN -> repeat(2) { audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
            Volume.MAX -> audio.setStreamVolume(stream, audio.getStreamMaxVolume(stream), AudioManager.FLAG_SHOW_UI)
            Volume.MUTE -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
            }
            Volume.UNMUTE -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                runCatching { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL }
            }
        }
        return Outcome(
            when (change) {
                Volume.UP -> "Listo, subí el volumen."
                Volume.DOWN -> "Listo, bajé el volumen."
                Volume.MAX -> "Volumen al máximo."
                Volume.MUTE -> "Listo, puse el teléfono en silencio (vibra)."
                Volume.UNMUTE -> "Listo, volvió el sonido."
            }
        )
    }

    private fun media(context: Context, key: Media): Outcome {
        val audio = context.getSystemService(AudioManager::class.java) ?: return Outcome("No pude controlar la música.")
        val code = when (key) {
            Media.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            Media.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            Media.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            Media.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Media.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        return Outcome(
            when (key) {
                Media.PAUSE -> "Listo, pausé."
                Media.PLAY -> "Listo, sigue sonando."
                Media.NEXT -> "Siguiente."
                Media.PREVIOUS -> "La anterior."
                Media.PLAY_PAUSE -> "Listo."
            }
        )
    }

    private fun doNotDisturb(context: Context, on: Boolean): Outcome {
        val filter = if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        // Con el "Acceso a notificaciones" de Ari se puede sin otro permiso.
        AriRecadosListener.instance?.let { listener ->
            if (runCatching { listener.requestInterruptionFilter(filter) }.isSuccess) {
                return Outcome(if (on) "Listo, activé No molestar." else "Listo, quité No molestar.")
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Outcome("No pude cambiar No molestar.")
        if (manager.isNotificationPolicyAccessGranted) {
            manager.setInterruptionFilter(filter)
            return Outcome(if (on) "Listo, activé No molestar." else "Listo, quité No molestar.")
        }
        return Outcome(
            "Para eso necesito el acceso a tus notificaciones (Ajustes → Leer mis notificaciones) o el permiso de No molestar. Te abro la pantalla.",
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        )
    }

    private fun brightness(context: Context, change: Brightness): Outcome {
        if (!Settings.System.canWrite(context)) {
            return Outcome(
                "Para cambiar el brillo, Android pide un permiso: activa \"Modificar ajustes del sistema\" para Ari y vuelve a pedírmelo. Te abro la pantalla.",
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            )
        }
        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, nextBrightness(current, change))
        return Outcome(
            when (change) {
                Brightness.UP -> "Listo, subí el brillo."
                Brightness.DOWN -> "Listo, bajé el brillo."
                Brightness.MAX -> "Brillo al máximo."
                Brightness.MIN -> "Brillo al mínimo."
            }
        )
    }
}

/**
 * Solo en la Ari CEO con Accesibilidad: "llama a mamá" marca y toca llamar sola.
 * Justo antes de abrir el marcador, Ari "arma" la llamada; el servicio de Accesibilidad
 * toca llamar solo si es reciente y el número en pantalla es ese.
 */
object AutoCall {
    const val VALID_MS = 15_000L

    data class Pending(val number: String, val at: Long)

    @Volatile
    private var pending: Pending? = null

    fun arm(number: String) {
        pending = Pending(number, SystemClock.elapsedRealtime())
    }

    fun current(): Pending? = pending?.takeIf { SystemClock.elapsedRealtime() - it.at < VALID_MS }

    fun disarm() {
        pending = null
    }

    /** true si [screen] muestra el número (se comparan los últimos 7 dígitos). */
    fun matches(screen: String, number: String): Boolean {
        val want = number.filter { it.isDigit() }.takeLast(7)
        if (want.length < 7) return false
        return screen.filter { it.isDigit() }.contains(want)
    }
}
