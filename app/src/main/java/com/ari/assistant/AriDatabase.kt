package com.ari.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ari.assistant.agents.MemoryStore
import com.ari.assistant.agents.Reminder
import com.ari.assistant.agents.PeopleStore
import com.ari.assistant.agents.Person
import com.ari.assistant.agents.ReminderStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ChatMessage(val role: String, val content: String, val id: Long = 0)

data class Memory(val id: Long, val content: String)

data class AgentTaskRecord(
    val agentId: String,
    val request: String,
    val reply: String,
    val success: Boolean
)

class AriDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), MemoryStore, ReminderStore, PeopleStore {

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
        createRemindersTable(db)
        createPeopleTable(db)
        createGameTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createAgentTasksTable(db)
        if (oldVersion < 3) createRemindersTable(db)
        if (oldVersion < 4) createPeopleTable(db)
        if (oldVersion < 5) createGameTables(db)
    }

    /**
     * Game Boost (versión 5): perfiles por juego, sesiones de juego y mediciones. Las
     * mediciones se enlazan con su sesión por la hora de inicio (started_at), no por el id,
     * porque el respaldo vuelve a numerar las filas. Todo es NOT NULL: el respaldo no sabe
     * guardar valores vacíos.
     */
    private fun createGameTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE game_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                game TEXT NOT NULL,
                title_id TEXT NOT NULL DEFAULT '',
                recommendations TEXT NOT NULL DEFAULT '',
                confidence TEXT NOT NULL DEFAULT 'baja',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE game_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                state TEXT NOT NULL,
                game TEXT NOT NULL DEFAULT '',
                eden_package TEXT NOT NULL DEFAULT '',
                eden_version TEXT NOT NULL DEFAULT '',
                ari_version TEXT NOT NULL DEFAULT '',
                device TEXT NOT NULL DEFAULT '',
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL DEFAULT 0,
                gemma_released INTEGER NOT NULL DEFAULT 0,
                ari_exit TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE game_measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_started_at INTEGER NOT NULL,
                moment TEXT NOT NULL,
                source TEXT NOT NULL,
                name TEXT NOT NULL,
                value TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    /** La libreta de personas (versión 4). facts: una por línea; birthday: "MM-DD" o vacío. */
    private fun createPeopleTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE people (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                relation TEXT NOT NULL DEFAULT '',
                facts TEXT NOT NULL DEFAULT '',
                birthday TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun people(): List<Person> {
        val result = mutableListOf<Person>()
        readableDatabase.query(
            "people", arrayOf("id", "name", "relation", "facts", "birthday"), null, null, null, null, "id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    Person(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        relation = cursor.getString(2),
                        facts = cursor.getString(3).split("\n").filter { it.isNotBlank() },
                        birthday = cursor.getString(4).ifEmpty { null }
                    )
                )
            }
        }
        return result
    }

    override fun savePerson(person: Person): Long {
        val values = ContentValues().apply {
            put("name", person.name)
            put("relation", person.relation)
            put("facts", person.facts.joinToString("\n") { it.replace("\n", " ") })
            put("birthday", person.birthday.orEmpty())
        }
        if (person.id != 0L) {
            writableDatabase.update("people", values, "id = ?", arrayOf(person.id.toString()))
            return person.id
        }
        values.put("created_at", System.currentTimeMillis())
        return writableDatabase.insert("people", null, values)
    }

    override fun deletePerson(id: Long) {
        writableDatabase.delete("people", "id = ?", arrayOf(id.toString()))
    }

    /** Recordatorios (versión 3). done: 0 pendiente, 1 avisado, 2 cancelado. */
    private fun createRemindersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                due_at INTEGER NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun addReminder(text: String, dueAt: LocalDateTime): Long {
        val values = ContentValues().apply {
            put("text", text)
            put("due_at", toMillis(dueAt))
            put("done", 0)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("reminders", null, values)
    }

    override fun pendingReminders(): List<Reminder> = queryReminders("done = 0", emptyArray())

    /** El recordatorio [id] si sigue pendiente. */
    fun pendingReminder(id: Long): Reminder? =
        queryReminders("done = 0 AND id = ?", arrayOf(id.toString())).firstOrNull()

    override fun cancelReminders(ids: List<Long>) = setReminderState(ids, 2)

    fun markReminderDone(id: Long) = setReminderState(listOf(id), 1)

    private fun setReminderState(ids: List<Long>, state: Int) {
        if (ids.isEmpty()) return
        val values = ContentValues().apply { put("done", state) }
        ids.forEach { writableDatabase.update("reminders", values, "id = ?", arrayOf(it.toString())) }
    }

    private fun queryReminders(where: String, args: Array<String>): List<Reminder> {
        val result = mutableListOf<Reminder>()
        readableDatabase.query("reminders", arrayOf("id", "text", "due_at"), where, args, null, null, "due_at ASC")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(Reminder(cursor.getLong(0), cursor.getString(1), fromMillis(cursor.getLong(2))))
                }
            }
        return result
    }

    private fun toMillis(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun fromMillis(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

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
    fun recentMessages(limit: Int): List<ChatMessage> = messagesBefore(Long.MAX_VALUE, limit)

    /** Los [limit] mensajes anteriores al mensaje [beforeId], en orden cronológico. */
    fun messagesBefore(beforeId: Long, limit: Int): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.query(
            "messages",
            arrayOf("role", "content", "id"),
            "id < ?",
            arrayOf(beforeId.toString()),
            null,
            null,
            "id DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(ChatMessage(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
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
                            in INT_COLUMNS -> values.put(column, row.optInt(column, 0))
                            in LONG_COLUMNS -> values.put(column, row.optLong(column, System.currentTimeMillis()))
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
                        in INT_COLUMNS -> row.put(column, cursor.getInt(index))
                        in LONG_COLUMNS -> row.put(column, cursor.getLong(index))
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
        private const val DATABASE_VERSION = 5

        /** Tablas y columnas que viajan en el respaldo (el id se vuelve a crear). */
        private val TABLES = listOf(
            "memories" to listOf("content", "created_at"),
            "messages" to listOf("role", "content", "created_at"),
            "agent_tasks" to listOf("agent_id", "request", "reply", "success", "created_at"),
            "reminders" to listOf("text", "due_at", "done", "created_at"),
            "people" to listOf("name", "relation", "facts", "birthday", "created_at"),
            "game_profiles" to listOf("game", "title_id", "recommendations", "confidence", "created_at"),
            "game_sessions" to listOf(
                "state", "game", "eden_package", "eden_version", "ari_version", "device",
                "started_at", "ended_at", "gemma_released", "ari_exit", "created_at"
            ),
            "game_measurements" to listOf("session_started_at", "moment", "source", "name", "value", "created_at")
        )

        /** Columnas que el respaldo guarda como número (el resto va como texto). */
        private val INT_COLUMNS = setOf("success", "done", "gemma_released")
        private val LONG_COLUMNS = setOf("created_at", "due_at", "started_at", "ended_at", "session_started_at")
    }
}
