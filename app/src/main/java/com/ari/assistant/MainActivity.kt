package com.ari.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    // Hilo del modelo: carga de Gemma y creación de conversaciones.
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    // Hilo de la base de datos, para no esperar a que cargue el modelo.
    private val dbExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private lateinit var db: AriDatabase

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var modelReady = false
    private var busy = false
    private var memoriesChanged = false

    private lateinit var status: TextView
    private lateinit var chat: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var selectButton: Button

    private val backgroundColorValue = Color.rgb(18, 18, 24)
    private val green = Color.rgb(120, 220, 170)
    private val white = Color.WHITE
    private val muted = Color.LTGRAY

    private val modelFile: File
        get() = File(filesDir, "gemma-model.litertlm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AriDatabase(this)
        buildInterface()
        restoreHistory()
        loadSavedModel()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 16)
            setBackgroundColor(backgroundColorValue)
        }

        val title = TextView(this).apply {
            text = "ARI"
            textSize = 32f
            setTextColor(white)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Tu asistente personal · IA local"
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "Selecciona tu modelo Gemma (.litertlm)"
            textSize = 14f
            setTextColor(green)
            setPadding(0, 16, 0, 12)
        }

        selectButton = Button(this).apply {
            text = "Seleccionar modelo"
            setOnClickListener { selectModel() }
        }

        val memoryButton = Button(this).apply {
            text = "Recuerdos"
            setOnClickListener { showMemoriesDialog() }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                selectButton,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                memoryButton,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        chat = TextView(this).apply {
            text = "Ari está esperando que cargues el modelo."
            textSize = 16f
            setTextColor(white)
            setPadding(8, 12, 8, 12)
            setTextIsSelectable(true)
        }

        scroll = ScrollView(this).apply {
            addView(
                chat,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        input = EditText(this).apply {
            hint = "Escribe un mensaje o /recuerda, /memoria, /olvida"
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            textSize = 16f
            setSingleLine(false)
            setBackgroundColor(Color.rgb(35, 35, 44))
            setPadding(12, 8, 12, 8)
        }

        sendButton = Button(this).apply {
            text = "Enviar"
            setOnClickListener { sendMessage() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(buttonRow)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(input)
        root.addView(sendButton)

        setContentView(root)
    }

    // ---------- Historial ----------

    private fun restoreHistory() {
        dbExecutor.execute {
            val history = try {
                db.recentMessages(HISTORY_DISPLAY_LIMIT)
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "No se pudo leer el historial: ${e.message}"
                }
                return@execute
            }

            if (history.isEmpty()) return@execute

            val text = history.joinToString("\n\n") { message ->
                "${speaker(message.role)}: ${message.text}"
            }

            runOnUiThread {
                chat.text = text
                scrollToBottom()
            }
        }
    }

    private fun speaker(role: String) =
        if (role == AriDatabase.ROLE_USER) "Tú" else "Ari"

    private fun saveMessage(role: String, text: String) {
        dbExecutor.execute {
            try {
                db.addMessage(role, text)
            } catch (_: Exception) {
            }
        }
    }

    private fun appendToChat(text: String) {
        chat.append(text)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ---------- Modelo ----------

    private fun loadSavedModel() {
        val file = modelFile
        if (!file.exists() || file.length() == 0L) return

        status.text = "Cargando el modelo guardado..."
        selectButton.isEnabled = false
        busy = true
        sendButton.isEnabled = false

        executor.execute { initializeModel(file) }
    }

    private fun selectModel() {
        if (busy) {
            status.text = "Espera a que Ari termine antes de cambiar el modelo."
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        @Suppress("DEPRECATION")
        startActivityForResult(intent, PICK_MODEL)
    }

    @Deprecated("Uses the compatible Android document picker result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != PICK_MODEL ||
            resultCode != RESULT_OK
        ) return

        val uri: Uri = data?.data ?: return

        status.text = "Preparando el archivo del modelo..."
        selectButton.isEnabled = false
        modelReady = false
        busy = true
        sendButton.isEnabled = false

        executor.execute {
            try {
                // Liberar el modelo anterior antes de sobrescribir su archivo.
                closeModel()

                val modelFile = modelFile
                val resolver = contentResolver

                val inputStream = resolver.openInputStream(uri)
                    ?: throw IllegalStateException(
                        "No se pudo abrir el archivo seleccionado."
                    )

                inputStream.use { source ->
                    modelFile.outputStream().buffered().use { destination ->
                        source.copyTo(destination, 1024 * 1024)
                    }
                }

                if (modelFile.length() == 0L) {
                    modelFile.delete()
                    throw IllegalStateException(
                        "El archivo seleccionado está vacío."
                    )
                }

                runOnUiThread {
                    status.text =
                        "Archivo copiado. Iniciando LiteRT-LM..."
                }

                initializeModel(modelFile)
            } catch (e: Exception) {
                runOnUiThread {
                    status.text =
                        "Error al preparar el modelo: ${e.message}"
                    selectButton.isEnabled = true
                    busy = false
                    sendButton.isEnabled = true
                }
            }
        }
    }

    /** Se ejecuta en [executor]. */
    private fun initializeModel(modelFile: File) {
        try {
            closeModel()

            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir.absolutePath
                )
            )

            newEngine.initialize()
            engine = newEngine
            conversation = newEngine.createConversation(buildConversationConfig())
            modelReady = true

            runOnUiThread {
                status.text = "Gemma cargado. Ari está listo."
                appendToChat("\n\nAri: ¡Hola! Ya estoy listo para conversar.")
                busy = false
                sendButton.isEnabled = true
                selectButton.isEnabled = true
            }
        } catch (e: Exception) {
            closeModel()
            runOnUiThread {
                status.text =
                    "No se pudo iniciar el modelo: ${e.message}"
                selectButton.isEnabled = true
                busy = false
                sendButton.isEnabled = true
            }
        }
    }

    /** Se ejecuta en [executor]. */
    private fun closeModel() {
        modelReady = false

        try {
            conversation?.close()
        } catch (_: Exception) {
        }
        conversation = null

        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }

    /**
     * Prepara la conversación con los recuerdos guardados como instrucción
     * del sistema y los últimos mensajes como contexto inicial.
     * Se ejecuta en [executor].
     */
    private fun buildConversationConfig(): ConversationConfig {
        val memories = try {
            db.listMemories()
        } catch (_: Exception) {
            emptyList()
        }

        val history = try {
            db.recentMessages(HISTORY_CONTEXT_LIMIT)
        } catch (_: Exception) {
            emptyList()
        }

        val instruction = buildString {
            append(SYSTEM_PROMPT)
            if (memories.isNotEmpty()) {
                append("\n\nRecuerdos guardados por el usuario:\n")
                memories.forEach { append("- ").append(it.text).append('\n') }
            }
        }

        return ConversationConfig(
            systemInstruction = Contents.of(instruction),
            initialMessages = trimHistory(history).map { message ->
                if (message.role == AriDatabase.ROLE_USER) {
                    Message.user(message.text)
                } else {
                    Message.model(message.text)
                }
            }
        )
    }

    /** Conserva los mensajes más recientes sin superar el límite de caracteres. */
    private fun trimHistory(history: List<ChatMessage>): List<ChatMessage> {
        val kept = ArrayDeque<ChatMessage>()
        var total = 0

        for (message in history.asReversed()) {
            val text = message.text.take(HISTORY_MESSAGE_MAX_CHARS)
            if (total + text.length > HISTORY_CONTEXT_MAX_CHARS) break
            total += text.length
            kept.addFirst(ChatMessage(message.role, text))
        }

        // La conversación debe empezar con un mensaje del usuario.
        while (kept.isNotEmpty() && kept.first().role != AriDatabase.ROLE_USER) {
            kept.removeFirst()
        }

        return kept.toList()
    }

    /** Recrea la conversación para que el modelo vea los recuerdos actualizados. */
    private fun refreshConversation() {
        if (!modelReady) return

        if (busy) {
            memoriesChanged = true
            return
        }

        memoriesChanged = false
        busy = true
        sendButton.isEnabled = false
        status.text = "Actualizando la memoria de Ari..."

        executor.execute {
            val activeEngine = engine
            try {
                if (activeEngine == null) return@execute
                conversation?.close()
                conversation = null
                conversation = activeEngine.createConversation(buildConversationConfig())
                runOnUiThread { status.text = "Gemma listo." }
            } catch (e: Exception) {
                modelReady = false
                runOnUiThread {
                    status.text = "No se pudo actualizar la conversación: ${e.message}"
                }
            } finally {
                runOnUiThread {
                    busy = false
                    sendButton.isEnabled = true
                }
            }
        }
    }

    // ---------- Mensajes y comandos ----------

    private fun sendMessage() {
        val text = input.text.toString().trim()

        if (text.isEmpty() || busy) return

        if (text.startsWith("/")) {
            input.text.clear()
            handleCommand(text)
            return
        }

        val activeConversation = conversation
        if (!modelReady || activeConversation == null) {
            status.text = "Primero carga el modelo Gemma."
            return
        }

        input.text.clear()
        appendToChat("\n\nTú: $text\n\nAri: ")
        saveMessage(AriDatabase.ROLE_USER, text)
        status.text = "Ari está pensando..."
        busy = true
        sendButton.isEnabled = false

        val reply = StringBuilder()

        activeConversation.sendMessageAsync(
            text,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val chunk = message.toString()
                    runOnUiThread {
                        reply.append(chunk)
                        appendToChat(chunk)
                    }
                }

                override fun onDone() {
                    runOnUiThread {
                        val answer = reply.toString().trim()
                        if (answer.isNotEmpty()) {
                            saveMessage(AriDatabase.ROLE_MODEL, answer)
                        }
                        busy = false
                        status.text = "Gemma listo."
                        sendButton.isEnabled = true
                        if (memoriesChanged) refreshConversation()
                    }
                }

                override fun onError(throwable: Throwable) {
                    runOnUiThread {
                        busy = false
                        status.text =
                            "Error de respuesta: ${throwable.message}"
                        appendToChat("\n[Error al generar la respuesta]")
                        sendButton.isEnabled = true
                        if (memoriesChanged) refreshConversation()
                    }
                }
            }
        )
    }

    private fun handleCommand(text: String) {
        val command = text.substringBefore(' ').lowercase()
        val argument = text.substringAfter(' ', "").trim()

        appendToChat("\n\nTú: $text")

        when (command) {
            "/recuerda" -> {
                if (argument.isEmpty()) {
                    appendToChat("\n\nAri: Usa /recuerda <texto> para guardar un recuerdo.")
                } else {
                    addMemory(argument)
                }
            }

            "/memoria", "/memorias" -> listMemoriesInChat()

            "/olvida" -> {
                if (argument.isEmpty()) {
                    appendToChat("\n\nAri: Usa /olvida <texto> para borrar los recuerdos que lo contengan.")
                } else {
                    forgetMemories(argument)
                }
            }

            else -> appendToChat(
                "\n\nAri: No conozco ese comando. Puedes usar:\n" +
                    "/recuerda <texto>\n/memoria\n/olvida <texto>"
            )
        }
    }

    private fun addMemory(text: String) {
        dbExecutor.execute {
            val reply = try {
                if (db.addMemory(text)) {
                    "Lo recordaré: $text"
                } else {
                    "Ya lo tenía guardado."
                }
            } catch (e: Exception) {
                "No pude guardar el recuerdo: ${e.message}"
            }

            runOnUiThread {
                appendToChat("\n\nAri: $reply")
                refreshConversation()
            }
        }
    }

    private fun listMemoriesInChat() {
        dbExecutor.execute {
            val reply = try {
                val memories = db.listMemories()
                if (memories.isEmpty()) {
                    "Todavía no tengo recuerdos guardados."
                } else {
                    "Esto es lo que recuerdo:\n" + memories
                        .mapIndexed { index, memory -> "${index + 1}. ${memory.text}" }
                        .joinToString("\n")
                }
            } catch (e: Exception) {
                "No pude leer los recuerdos: ${e.message}"
            }

            runOnUiThread { appendToChat("\n\nAri: $reply") }
        }
    }

    private fun forgetMemories(fragment: String) {
        dbExecutor.execute {
            var deleted = 0
            val reply = try {
                deleted = db.deleteMemoriesContaining(fragment)
                when (deleted) {
                    0 -> "No encontré recuerdos con \"$fragment\"."
                    1 -> "Olvidé 1 recuerdo."
                    else -> "Olvidé $deleted recuerdos."
                }
            } catch (e: Exception) {
                "No pude borrar los recuerdos: ${e.message}"
            }

            runOnUiThread {
                appendToChat("\n\nAri: $reply")
                if (deleted > 0) refreshConversation()
            }
        }
    }

    // ---------- Ventana de recuerdos ----------

    private fun showMemoriesDialog() {
        dbExecutor.execute {
            val memories = try {
                db.listMemories()
            } catch (_: Exception) {
                emptyList()
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                val builder = AlertDialog.Builder(this)
                    .setTitle("Recuerdos de Ari")
                    .setPositiveButton("Agregar") { _, _ -> showAddMemoryDialog() }
                    .setNegativeButton("Cerrar", null)

                if (memories.isEmpty()) {
                    builder.setMessage("Todavía no hay recuerdos. Toca \"Agregar\" para crear uno.")
                } else {
                    builder.setItems(
                        memories.map { it.text }.toTypedArray()
                    ) { _, which -> confirmDeleteMemory(memories[which]) }
                }

                builder.show()
            }
        }
    }

    private fun showAddMemoryDialog() {
        val field = EditText(this).apply {
            hint = "Por ejemplo: Mi color favorito es el azul"
            setSingleLine(false)
        }

        AlertDialog.Builder(this)
            .setTitle("Nuevo recuerdo")
            .setView(field)
            .setPositiveButton("Guardar") { _, _ ->
                val text = field.text.toString().trim()
                if (text.isNotEmpty()) addMemory(text)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteMemory(memory: Memory) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar recuerdo?")
            .setMessage(memory.text)
            .setPositiveButton("Borrar") { _, _ ->
                dbExecutor.execute {
                    val deleted = try {
                        db.deleteMemory(memory.id)
                    } catch (_: Exception) {
                        false
                    }

                    runOnUiThread {
                        if (deleted) {
                            appendToChat("\n\nAri: Olvidé: ${memory.text}")
                            refreshConversation()
                        } else {
                            status.text = "No se pudo borrar el recuerdo."
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.execute { closeModel() }
        executor.shutdown()

        dbExecutor.execute { db.close() }
        dbExecutor.shutdown()
    }

    companion object {
        private const val PICK_MODEL = 101

        // Mensajes que se muestran en pantalla al abrir Ari.
        private const val HISTORY_DISPLAY_LIMIT = 200

        // Contexto que recibe el modelo, limitado para no llenar su ventana.
        private const val HISTORY_CONTEXT_LIMIT = 20
        private const val HISTORY_CONTEXT_MAX_CHARS = 4000
        private const val HISTORY_MESSAGE_MAX_CHARS = 1200

        private const val SYSTEM_PROMPT =
            "Tu nombre es Ari. Hablas español de forma natural y clara. " +
                "Utilizas los recuerdos disponibles cuando son relevantes. " +
                "No inventas recuerdos ni afirmas haber hecho acciones que no realizaste. " +
                "Si no sabes algo, lo reconoces."
    }
}
