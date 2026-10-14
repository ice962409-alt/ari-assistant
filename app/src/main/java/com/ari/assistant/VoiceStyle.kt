package com.ari.assistant

import android.content.SharedPreferences

/**
 * Cómo suena ARI: la velocidad con que lee y el tono (más grave o más agudo).
 * Se guarda en las preferencias de voz y se aplica al motor del teléfono (TextToSpeech).
 * Lógica pura, sin Android, para poder probarla.
 */
object VoiceStyle {

    private const val KEY_RATE = "velocidad_voz"
    private const val KEY_PITCH = "tono_voz"

    /** Velocidades que puede elegir el usuario (nombre y multiplicador del motor). */
    val SPEEDS = listOf(
        "Lenta" to 0.8f,
        "Normal" to 1.0f,
        "Rápida" to 1.25f,
        "Muy rápida" to 1.5f,
    )

    /** Tonos que puede elegir (nombre y multiplicador del motor). */
    val PITCHES = listOf(
        "Grave" to 0.85f,
        "Normal" to 1.0f,
        "Agudo" to 1.2f,
    )

    private const val DEFAULT_RATE = 1.0f
    private const val DEFAULT_PITCH = 1.0f

    /** El motor acepta de 0.1 a varias veces; se limita a un rango con sentido para una voz. */
    fun clampRate(value: Float) = value.coerceIn(0.5f, 2.0f)
    fun clampPitch(value: Float) = value.coerceIn(0.5f, 2.0f)

    fun rate(prefs: SharedPreferences): Float = clampRate(prefs.getFloat(KEY_RATE, DEFAULT_RATE))
    fun pitch(prefs: SharedPreferences): Float = clampPitch(prefs.getFloat(KEY_PITCH, DEFAULT_PITCH))

    fun setRate(prefs: SharedPreferences, value: Float) {
        prefs.edit().putFloat(KEY_RATE, clampRate(value)).apply()
    }

    fun setPitch(prefs: SharedPreferences, value: Float) {
        prefs.edit().putFloat(KEY_PITCH, clampPitch(value)).apply()
    }

    /** El nombre de la opción más cercana al valor guardado (para marcarla en la lista). */
    fun label(options: List<Pair<String, Float>>, value: Float): String =
        options.minByOrNull { kotlin.math.abs(it.second - value) }?.first ?: options[0].first

    fun rateLabel(prefs: SharedPreferences) = label(SPEEDS, rate(prefs))
    fun pitchLabel(prefs: SharedPreferences) = label(PITCHES, pitch(prefs))
}
