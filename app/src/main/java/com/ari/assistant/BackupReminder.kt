package com.ari.assistant

import android.content.SharedPreferences

/**
 * Recordatorio de respaldo: si Ari tiene recuerdos y hace una semana o más
 * que no se guarda un respaldo, lo dice al abrir la app (como mucho una vez al día).
 */
object BackupReminder {

    const val PREFS = "ari_respaldo"
    private const val KEY_LAST_BACKUP = "ultimo_respaldo"
    private const val KEY_LAST_REMINDER = "ultimo_aviso"

    private const val DAY_MS = 24L * 60 * 60 * 1000
    const val INTERVAL_DAYS = 7

    fun markDone(prefs: SharedPreferences, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
    }

    fun message(prefs: SharedPreferences, memories: Int, now: Long): String? {
        val text = reminderText(
            prefs.getLong(KEY_LAST_BACKUP, 0L),
            prefs.getLong(KEY_LAST_REMINDER, 0L),
            memories,
            now
        ) ?: return null
        prefs.edit().putLong(KEY_LAST_REMINDER, now).apply()
        return text
    }

    /** La regla, sin Android, para poder probarla. 0 significa "nunca". */
    fun reminderText(lastBackup: Long, lastReminder: Long, memories: Int, now: Long): String? {
        if (memories == 0) return null
        if (lastReminder != 0L && now - lastReminder < DAY_MS) return null
        if (lastBackup == 0L) {
            return "Todavía no has guardado un respaldo de mis recuerdos. Hazlo en Ajustes → Respaldo de la memoria y guárdalo en tu Drive."
        }
        val days = (now - lastBackup) / DAY_MS
        if (days < INTERVAL_DAYS) return null
        return "Hace $days días que no guardas un respaldo de mis recuerdos. Hazlo en Ajustes → Respaldo de la memoria."
    }
}
