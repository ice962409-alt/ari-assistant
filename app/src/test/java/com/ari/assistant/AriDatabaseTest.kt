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
        assertEquals(4, upgraded.readableDatabase.version)
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
    fun forgettingOnlyRemovesWholeWords() {
        val db = open()
        db.addMemory("me gusta el café")
        db.addMemory("trabajo en una cafetería")

        assertEquals(1, db.deleteMemoriesMatching("café"))
        assertEquals(listOf("trabajo en una cafetería"), db.memories().map { it.content })
    }

    @Test
    fun backupRestoresEverythingAndReplacesNewerData() {
        val source = open()
        source.addMessage("user", "antes del respaldo")
        source.addMemory("recuerdo del respaldo")
        source.savePerson(Person(id = 0, name = "Ana", facts = listOf("jefa", "cumple en mayo"), birthday = "05-10"))
        source.addReminder("tomar agua", LocalDateTime.of(2030, 1, 1, 9, 0))
        val backup = source.exportData()

        source.addMemory("recuerdo nuevo, después del respaldo")
        source.importData(backup)

        assertEquals(listOf("recuerdo del respaldo"), source.memories().map { it.content })
        assertEquals(listOf("antes del respaldo"), source.recentMessages(30).map { it.content })
        val ana = source.people().single()
        assertEquals(listOf("jefa", "cumple en mayo"), ana.facts)
        assertEquals("05-10", ana.birthday)
        assertEquals(listOf("tomar agua"), source.pendingReminders().map { it.text })
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
