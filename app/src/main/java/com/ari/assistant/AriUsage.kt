package com.ari.assistant

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.ari.assistant.agents.normalizeForAgents
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Solo en la Ari CEO: cuánto usas el teléfono y cuánto caminas.
 * - "¿cuánto tiempo usé el teléfono hoy?", "¿en qué apps paso más tiempo?" (permiso "Acceso al uso").
 * - "¿cuántos pasos llevo?" (sensor de pasos del teléfono).
 * Nada sale del teléfono. Las frases y los textos no usan Android, para poder probarlos.
 */
object AriUsage {

    enum class Request { SCREEN_TIME, STEPS }

    data class AppTime(val name: String, val minutes: Long)

    private val SCREEN = Regex(
        "^(?:cuanto tiempo|cuantas horas)\\s+(?:he usado|use|uso|llevo usando|llevo en|estuve en|pase en)\\s+(?:el|mi)\\s+(?:telefono|celular|cel)(?:\\s+hoy)?$" +
            "|^(?:mi\\s+|el\\s+)?tiempo (?:en|de) pantalla(?:\\s+de hoy|\\s+hoy)?$" +
            "|^(?:en )?que apps? (?:uso|use|he usado|paso|pase) mas(?:\\s+tiempo)?(?:\\s+hoy)?$" +
            "|^cuanto (?:uso|use) (?:el|mi) (?:telefono|celular)(?:\\s+hoy)?$"
    )
    private val STEPS = Regex("^(?:cuantos pasos (?:llevo|he dado|di|camine|llevo hoy|he caminado)(?:\\s+hoy)?|mis pasos(?:\\s+de hoy)?|cuanto (?:he caminado|camine|llevo caminado)(?:\\s+hoy)?)$")

    fun parse(text: String): Request? {
        val t = normalizeForAgents(text)
        return when {
            SCREEN.matches(t) -> Request.SCREEN_TIME
            STEPS.matches(t) -> Request.STEPS
            else -> null
        }
    }

    fun duration(minutes: Long): String = when {
        minutes < 1 -> "menos de un minuto"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /** "Hoy usaste el teléfono 3 h 20 min. Lo que más: WhatsApp 1 h, YouTube 45 min…" */
    fun screenTimeMessage(apps: List<AppTime>): String {
        val used = apps.filter { it.minutes >= 1 }.sortedByDescending { it.minutes }
        if (used.isEmpty()) return "Hoy casi no has usado el teléfono."
        val total = used.sumOf { it.minutes }
        val top = used.take(5).joinToString(", ") { "${it.name} ${duration(it.minutes)}" }
        val advice = if (total >= 300) " Es bastante: ¿te pongo un recordatorio para descansar la vista?" else ""
        return "Hoy usaste el teléfono ${duration(total)}. Lo que más: $top.$advice"
    }

    /** Pasos de hoy a partir del contador del teléfono (cuenta desde que se prendió). */
    fun stepsToday(counter: Long, savedDay: String?, savedBase: Long, today: String): Pair<Long, Long> {
        // El contador vuelve a 0 al reiniciar el teléfono; un día nuevo empieza de cero.
        val base = if (savedDay != today || counter < savedBase) counter else savedBase
        return (counter - base) to base
    }

    // --- En el teléfono ---

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent() = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appTimesToday(context: Context): List<AppTime> {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stats = usage.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        val pm = context.packageManager
        val launcher = pm.resolveActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName
        return stats.values
            .filter { it.packageName != launcher && it.totalTimeInForeground > 60_000 }
            .map { stat ->
                val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString() }
                    .getOrDefault(stat.packageName)
                AppTime(name, stat.totalTimeInForeground / 60_000)
            }
            .groupBy { it.name }
            .map { (name, list) -> AppTime(name, list.sumOf { it.minutes }) }
    }

    /** Lee el contador de pasos (espera como mucho 3 s). null si el teléfono no tiene sensor. */
    fun readStepCounter(context: Context): Long? {
        val sensors = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        var value: Long? = null
        val done = CountDownLatch(1)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                value = event.values.firstOrNull()?.toLong()
                done.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(Looper.getMainLooper()))
        done.await(3, TimeUnit.SECONDS)
        sensors.unregisterListener(listener)
        return value
    }

    /** Pasos de hoy y desde qué hora cuento (el contador del teléfono no sabe de días). */
    fun steps(context: Context): Pair<Long, String>? {
        val counter = readStepCounter(context) ?: return null
        val prefs = context.getSharedPreferences("ari_pasos", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val savedDay = prefs.getString("dia", null)
        val savedBase = prefs.getLong("base", 0)
        val (steps, base) = stepsToday(counter, savedDay, savedBase, today)
        val since = if (base != savedBase || savedDay != today) {
            java.time.LocalTime.now().withSecond(0).withNano(0).toString()
        } else {
            prefs.getString("desde", "00:00") ?: "00:00"
        }
        prefs.edit().putString("dia", today).putLong("base", base).putString("desde", since).apply()
        return steps to since
    }

    /** Se llama al abrir Ari: así el día empieza a contarse lo antes posible. */
    fun sample(context: Context) {
        if (context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        Thread { runCatching { steps(context) } }.start()
    }

    fun stepsMessage(steps: Long, since: String): String {
        val from = if (since == "00:00") "hoy" else "desde las $since"
        val note = if (since != "00:00") " (cuento desde que me abriste por primera vez hoy; ábreme temprano y cuento todo el día)" else ""
        val cheer = when {
            steps >= 10_000 -> " ¡Excelente!"
            steps >= 6_000 -> " ¡Vas muy bien!"
            else -> ""
        }
        return "Llevas $steps pasos $from.$cheer$note"
    }
}
