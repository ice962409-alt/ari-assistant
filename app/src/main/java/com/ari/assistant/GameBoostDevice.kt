package com.ari.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.ari.assistant.agents.GameBoost
import com.ari.assistant.agents.GameBoostReader
import com.ari.assistant.agents.GameSnapshot

/**
 * Game Boost: mide el teléfono con lo que Android deja leer a cualquier app. Todo es del
 * teléfono entero (nada de Eden por dentro) y solo se lee: no cambia ningún ajuste.
 * Corre fuera del hilo principal.
 */
class GameBoostDevice(
    context: Context,
    private val gemmaLoaded: () -> Boolean,
    private val gemmaLoading: () -> Boolean = { false }
) : GameBoostReader {

    private val context = context.applicationContext

    override fun read(): GameSnapshot {
        val memory = ActivityManager.MemoryInfo()
        runCatching { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory) }
        val power = context.getSystemService(PowerManager::class.java)
        val battery = runCatching { batteryIntent() }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return GameSnapshot(
            availMemBytes = memory.availMem,
            totalMemBytes = memory.totalMem,
            lowMemory = memory.lowMemory,
            // Memoria propia de ARI (PSS, en KB). Gemma vive en este proceso, pero lo que ocupa en la GPU no entra aquí.
            ariMemBytes = runCatching { android.os.Debug.getPss() * 1024L }.getOrDefault(0L),
            gemmaLoaded = gemmaLoaded(),
            gemmaLoading = gemmaLoading(),
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { power?.currentThermalStatus }.getOrNull() else null,
            thermalHeadroom = headroom(power),
            batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else null,
            batteryTenthsC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE },
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            powerSave = runCatching { power?.isPowerSaveMode == true }.getOrDefault(false),
            chip = chip(),
            eden = runCatching { findEden() }.getOrNull()
        )
    }

    /** El aviso fijo de la batería que Android guarda (no hace falta escucharlo). */
    private fun batteryIntent(): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(null, filter)
        }
    }

    /**
     * Margen térmico (Android 11+). Android pide no consultarlo más de una vez cada 10
     * segundos: si se pide antes, se repite la lectura anterior.
     */
    private fun headroom(power: PowerManager?): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || power == null) return null
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(GameBoostDevice) {
            if (lastHeadroomAt != 0L && now - lastHeadroomAt < HEADROOM_INTERVAL_MS) return lastHeadroom
            lastHeadroom = runCatching { power.getThermalHeadroom(0) }.getOrNull()
            lastHeadroomAt = now
            return lastHeadroom
        }
    }

    private fun chip(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ""
        return listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            .filter { it.isNotBlank() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
            .joinToString(" ")
    }

    /**
     * Los paquetes conocidos de Eden y, solo si no hay ninguno, las apps del lanzador que se
     * llamen "Eden" (de esa se pide la versión al final, no de todas).
     */
    private fun findEden(): com.ari.assistant.agents.EdenApp? {
        val known = GameBoost.EDEN_PACKAGES.mapNotNull { pkg -> version(pkg)?.let { pkg to it } }.toMap()
        GameBoost.pickEden(known, emptyList())?.let { return it }
        return GameBoost.pickEden(emptyMap(), runCatching { launcherApps() }.getOrDefault(emptyList()))
            ?.let { it.copy(version = version(it.packageName).orEmpty()) }
    }

    private fun version(pkg: String): String? = try {
        context.packageManager.getPackageInfo(pkg, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun launcherApps(): List<Triple<String, String, String>> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { Triple(it.loadLabel(pm).toString(), it.activityInfo.packageName, "") }
    }

    private companion object {
        const val HEADROOM_INTERVAL_MS = 10_000L
        var lastHeadroom: Float? = null
        var lastHeadroomAt = 0L
    }
}
