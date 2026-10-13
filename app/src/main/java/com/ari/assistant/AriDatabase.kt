package com.ari.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ari.assistant.agents.MemoryStore
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val role: String, val content: String)

data class Memory(val id: Long, val content: String)

data class AgentTaskRecord(
    val agentId: String,
    val request: String,
    val reply: String,
    val success: Boolean
)

class AriDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), MemoryStore {

    init {
        // WAL: las lecturas no esperan a las escrituras de otros hilos (los
        // agentes y el guardado en segundo plano) y cada escritura es más rápida.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        createAgentTasksTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createAgentTasksTable(db)
    }

    /** Bitácora de las tareas que el CEO delega en sus agentes (versión 2). */
    private fun createAgentTasksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE agent_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                agent_id TEXT NOT NULL,
                request TEXT NOT NULL,
                reply TEXT NOT NULL,
                success INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    fun addMessage(role: String, content: String) {
        val values = ContentValues().apply {
            put("role", role)
            put("content", content)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("messages", null, values)
    }

    /** Devuelve los últimos [limit] mensajes en orden cronológico. */
    fun recentMessages(limit: Int): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.query(
            "messages",
            arrayOf("role", "content"),
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

    fun clearMessages() {
        writableDatabase.delete("messages", null, null)
    }

    override fun addMemory(content: String): Long {
        val values = ContentValues().apply {
            put("content", content)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("memories", null, values)
    }

    override fun memories(): List<Memory> {
        val result = mutableListOf<Memory>()
        readableDatabase.query(
            "memories",
            arrayOf("id", "content"),
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

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memories", "id = ?", arrayOf(id.toString()))
    }

    /**
     * Borra los recuerdos que contienen [text] como palabra o frase completa,
     * sin distinguir mayúsculas: "olvida café" no borra "cafetería".
     */
    override fun deleteMemoriesMatching(text: String): Int {
        val needle = Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(text.trim()) + "(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
        val matches = memories().filter { needle.containsMatchIn(it.content) }
        matches.forEach { deleteMemory(it.id) }
        return matches.size
    }

    fun addAgentTask(agentId: String, request: String, reply: String, success: Boolean) {
        val values = ContentValues().apply {
            put("agent_id", agentId)
            put("request", request)
            put("reply", reply)
            put("success", if (success) 1 else 0)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("agent_tasks", null, values)
    }

    /** Devuelve las últimas [limit] tareas de agentes, de la más reciente a la más antigua. */
    fun recentAgentTasks(limit: Int): List<AgentTaskRecord> {
        val result = mutableListOf<AgentTaskRecord>()
        readableDatabase.query(
            "agent_tasks",
            arrayOf("agent_id", "request", "reply", "success"),
            null,
            null,
            null,
            null,
            "id DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    AgentTaskRecord(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3) == 1
                    )
                )
            }
        }
        return result
    }

    /** Copia de todas las tablas para el respaldo cifrado (ver [AriBackup]). */
    fun exportData(): JSONObject = JSONObject().apply {
        TABLES.forEach { (table, columns) -> put(table, exportTable(table, columns)) }
    }

    /** Reemplaza todo con lo que trae un respaldo. Si algo falla, no cambia nada. */
    fun importData(data: JSONObject) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            TABLES.forEach { (table, columns) ->
                db.delete(table, null, null)
                val rows = data.optJSONArray(table) ?: JSONArray()
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val values = ContentValues()
                    columns.forEach { column ->
                        when (column) {
                            "success" -> values.put(column, row.optInt(column, 0))
                            "created_at" -> values.put(column, row.optLong(column, System.currentTimeMillis()))
                            else -> values.put(column, row.getString(column))
                        }
                    }
                    db.insertOrThrow(table, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun exportTable(table: String, columns: List<String>): JSONArray {
        val rows = JSONArray()
        readableDatabase.query(table, columns.toTypedArray(), null, null, null, null, "id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                columns.forEachIndexed { index, column ->
                    when (column) {
                        "success" -> row.put(column, cursor.getInt(index))
                        "created_at" -> row.put(column, cursor.getLong(index))
                        else -> row.put(column, cursor.getString(index))
                    }
                }
                rows.put(row)
            }
        }
        return rows
    }

    companion object {
        private const val DATABASE_NAME = "ari.db"
        private const val DATABASE_VERSION = 2

        /** Tablas y columnas que viajan en el respaldo (el id se vuelve a crear). */
        private val TABLES = listOf(
            "memories" to listOf("content", "created_at"),
            "messages" to listOf("role", "content", "created_at"),
            "agent_tasks" to listOf("agent_id", "request", "reply", "success", "created_at")
        )
    }
}
