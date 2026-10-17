package com.ari.assistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.ari.assistant.agents.Person
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * La base de datos real (SQLite) de Ari: que lo guardado siga ahí al volver a abrirla y que
 * las migraciones no borren nada. Apoya el criterio C de la Fase 1; la prueba de forzar el
 * cierre en el teléfono sigue haciéndola el usuario.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AriDatabaseTest {

    private lateinit var context: Context
    private val opened = mutableListOf<AriDatabase>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        context.deleteDatabase(DB_NAME)
    }

    private fun open(): AriDatabase = AriDatabase(context).also { opened.add(it) }

    @Test
    fun keepsMessagesMemoriesAndPeopleAfterReopening() {
        val first = open()
        first.addMessage("user", "Prueba C: contesta solo con la palabra listo")
        first.addMessage("ari", "listo")
        first.addMemory("mi palabra de prueba es PRUEBAC")
        first.savePerson(Person(id = 0, name = "Pruebac", relation = "vecino", facts = listOf("prueba C")))
        first.addAgentTask("memoria", "recuerda que …", "Lo recordaré", success = true)
        first.close()

        val second = open()
        assertEquals(
            listOf("user" to "Prueba C: contesta solo con la palabra listo", "ari" to "listo"),
            second.recentMessages(30).map { it.role to it.content }
        )
        assertEquals(listOf("mi palabra de prueba es PRUEBAC"), second.memories().map { it.content })
        val person = second.people().single()
        assertEquals("Pruebac", person.name)
        assertEquals("vecino", person.relation)
        assertEquals(listOf("prueba C"), person.facts)
        assertEquals("memoria", second.recentAgentTasks(5).single().agentId)
    }

    @Test
    fun upgradingFromVersion1KeepsHistoryAndMemories() {
        // Base como la de main (0.1.0): solo messages y memories, versión 1.
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, " +
                    "content TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO messages (role, content, created_at) VALUES ('user', 'hola de la 0.1', 1)")
            db.execSQL("INSERT INTO memories (content, created_at) VALUES ('me gusta el café', 1)")
            db.version = 1
        }

        val upgraded = open()
        assertEquals(5, upgraded.readableDatabase.version)
        assertEquals(listOf("hola de la 0.1"), upgraded.recentMessages(30).map { it.content })
        assertEquals(listOf("me gusta el café"), upgraded.memories().map { it.content })
        // Las tablas nuevas existen y están vacías.
        assertTrue(upgraded.people().isEmpty())
        assertTrue(upgraded.pendingReminders().isEmpty())
        assertTrue(upgraded.recentAgentTasks(5).isEmpty())
        upgraded.savePerson(Person(id = 0, name = "Luis"))
        assertEquals("Luis", upgraded.people().single().name)
    }

    @Test
    fun upgradingFromVersion4KeepsEverythingAndAddsGameBoost() {
        // Base como la de la 0.67.0: cinco tablas, versión 4.
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL(
                "CREATE TABLE agent_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, agent_id TEXT NOT NULL, request TEXT NOT NULL, " +
                    "reply TEXT NOT NULL, success INTEGER NOT NULL, created_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, due_at INTEGER NOT NULL, " +
                    "done INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE people (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, relation TEXT NOT NULL DEFAULT '', " +
                    "facts TEXT NOT NULL DEFAULT '', birthday TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO messages (role, content, created_at) VALUES ('user', 'hola de la 0.67', 1)")
            db.execSQL("INSERT INTO memories (content, created_at) VALUES ('juego en el POCO', 1)")
            db.execSQL("INSERT INTO people (name, relation, created_at) VALUES ('Luis', 'primo', 1)")
            db.version = 4
        }

        val upgraded = open()
        assertEquals(5, upgraded.readableDatabase.version)
        assertEquals(listOf("hola de la 0.67"), upgraded.recentMessages(30).map { it.content })
        assertEquals(listOf("juego en el POCO"), upgraded.memories().map { it.content })
        assertEquals("Luis", upgraded.people().single().name)
        listOf("game_profiles", "game_sessions", "game_measurements").forEach { table ->
            upgraded.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun forgettingOnlyRemovesWholeWords() {
        // "cafe" sí está dentro de "cafetería": un contains() simple borraría los dos recuerdos.
        val db = open()
        db.addMemory("me gusta el Cafe")
        db.addMemory("trabajo en una cafetería")

        assertEquals(1, db.deleteMemoriesMatching("cafe"))
        assertEquals(listOf("trabajo en una cafetería"), db.memories().map { it.content })
    }

    @Test
    fun backupRestoresEverythingAndReplacesNewerData() {
        val source = open()
        source.addMessage("user", "antes del respaldo")
        source.addMemory("recuerdo del respaldo")
        source.savePerson(Person(id = 0, name = "Ana", facts = listOf("jefa", "cumple en mayo"), birthday = "05-10"))
        source.addReminder("tomar agua", LocalDateTime.of(2030, 1, 1, 9, 0))
        source.writableDatabase.execSQL(
            "INSERT INTO game_sessions (state, game, eden_version, started_at, gemma_released, created_at) " +
                "VALUES ('CERRADA', 'Zelda', '1.0.0', 1700000000000, 1, 1)"
        )
        source.writableDatabase.execSQL(
            "INSERT INTO game_measurements (session_started_at, moment, source, name, value, created_at) " +
                "VALUES (1700000000000, 'antes', 'medido', 'memoria_libre_mb', '5200', 1)"
        )
        val backup = source.exportData()
        // Los números de Game Boost viajan como números, no como texto.
        val session = backup.getJSONArray("game_sessions").getJSONObject(0)
        assertTrue(session.get("started_at") is Long)
        assertTrue(session.get("gemma_released") is Int)
        assertTrue(backup.getJSONArray("game_measurements").getJSONObject(0).get("session_started_at") is Long)

        // Lo de Game Boost posterior al respaldo desaparece al restaurar, y lo de antes vuelve.
        source.writableDatabase.execSQL("DELETE FROM game_measurements")
        source.writableDatabase.execSQL("INSERT INTO game_sessions (state, started_at, created_at) VALUES ('EN_CURSO', 1800000000000, 2)")
        source.addMemory("recuerdo nuevo, después del respaldo")
        source.importData(backup)

        assertEquals(listOf("recuerdo del respaldo"), source.memories().map { it.content })
        assertEquals(listOf("antes del respaldo"), source.recentMessages(30).map { it.content })
        val ana = source.people().single()
        assertEquals(listOf("jefa", "cumple en mayo"), ana.facts)
        assertEquals("05-10", ana.birthday)
        assertEquals(listOf("tomar agua"), source.pendingReminders().map { it.text })
        // Game Boost viaja en el respaldo, con sus números y el enlace por la hora de inicio.
        source.readableDatabase.rawQuery(
            "SELECT s.game, s.started_at, s.gemma_released, m.value FROM game_sessions s " +
                "JOIN game_measurements m ON m.session_started_at = s.started_at",
            null
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("Zelda", it.getString(0))
            assertEquals(1700000000000L, it.getLong(1))
            assertEquals(1, it.getInt(2))
            assertEquals("5200", it.getString(3))
        }
        source.readableDatabase.rawQuery("SELECT COUNT(*) FROM game_sessions", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun olderMessagesComeInOrder() {
        val db = open()
        (1..5).forEach { db.addMessage("user", "mensaje $it") }

        val lastTwo = db.recentMessages(2)
        assertEquals(listOf("mensaje 4", "mensaje 5"), lastTwo.map { it.content })
        assertEquals(
            listOf("mensaje 2", "mensaje 3"),
            db.messagesBefore(lastTwo.first().id, 2).map { it.content }
        )
    }

    private companion object {
        const val DB_NAME = "ari.db"
    }
}
