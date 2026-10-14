package com.ari.assistant

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.ari.assistant.agents.CalendarItem
import com.ari.assistant.agents.CalendarReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Lee los eventos de los calendarios del teléfono (solo lectura, nada sale del teléfono). */
class AndroidCalendar(context: Context) : CalendarReader {

    private val context = context.applicationContext

    override fun events(from: LocalDateTime, to: LocalDateTime): List<CalendarItem>? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val zone = ZoneId.systemDefault()
        val begin = from.atZone(zone).toInstant().toEpochMilli()
        val end = to.atZone(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
        return runCatching {
            context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                val items = mutableListOf<CalendarItem>()
                while (cursor.moveToNext()) {
                    val allDay = cursor.getInt(2) == 1
                    val millis = cursor.getLong(1)
                    // Los eventos de todo el día se guardan en UTC: se leen como fecha, sin mover la hora.
                    val start = if (allDay) LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
                    else LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
                    items.add(CalendarItem(cursor.getString(0) ?: "", start, allDay))
                }
                items
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
