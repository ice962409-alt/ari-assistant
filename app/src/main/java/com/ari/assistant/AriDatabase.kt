package com.ari.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ChatMessage(
    val role: String,
    val text: String
)

data class Memory(
    val id: Long,
    val text: String
)

class AriDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Primera versión del esquema: todavía no hay migraciones.
    }

    fun addMessage(role: String, text: String) {
        val values = ContentValues().apply {
            put("role", role)
            put("text", text)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("messages", null, values)
    }

    /** Devuelve los últimos [limit] mensajes en orden cronológico. */
    fun recentMessages(limit: Int): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()

        readableDatabase.query(
            "messages",
            arrayOf("role", "text"),
            null,
            null,
            null,
            null,
            "id DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(ChatMessage(cursor.getString(0), cursor.getString(1)))
            }
        }

        return result.asReversed()
    }

    /** Devuelve false si el recuerdo ya existía. */
    fun addMemory(text: String): Boolean {
        val values = ContentValues().apply {
            put("text", text)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(
            "memories",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun listMemories(): List<Memory> {
        val result = mutableListOf<Memory>()

        readableDatabase.query(
            "memories",
            arrayOf("id", "text"),
            null,
            null,
            null,
            null,
            "id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Memory(cursor.getLong(0), cursor.getString(1)))
            }
        }

        return result
    }

    fun deleteMemory(id: Long): Boolean =
        writableDatabase.delete(
            "memories",
            "id = ?",
            arrayOf(id.toString())
        ) > 0

    /** Borra los recuerdos que contienen [fragment]. Devuelve cuántos borró. */
    fun deleteMemoriesContaining(fragment: String): Int {
        val escaped = fragment
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        return writableDatabase.delete(
            "memories",
            "text LIKE ? ESCAPE '\\'",
            arrayOf("%$escaped%")
        )
    }

    companion object {
        private const val DATABASE_NAME = "ari.db"
        private const val DATABASE_VERSION = 1

        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}
