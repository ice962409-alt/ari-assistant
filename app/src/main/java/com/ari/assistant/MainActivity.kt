package com.ari.assistant

import android.app.Activity
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ari.assistant.agents.AriCeo
import com.ari.assistant.agents.ClockAgent
import com.ari.assistant.agents.MemoryAgent
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var modelReady = false
    private var busy = false

    private lateinit var database: AriDatabase

    // Ari dirige a sus agentes: cada mensaje pasa primero por el CEO, y solo
    // si ningún agente sabe atenderlo se envía a Gemma.
    private lateinit var ceo: AriCeo

    // El modelo no conoce el historial guardado ni los recuerdos hasta que
    // se los enviamos; estos indicadores marcan qué falta por enviarle.
    private var historyPending = true
    private var memoriesPending = true

    private lateinit var status: TextView
    private lateinit var chat: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var selectButton: Button
    private lateinit var memoryButton: Button
    private lateinit var voiceButton: Button

    private lateinit var voice: VoiceController

    // Modo voz: Ari escucha, responde y lee la respuesta en voz alta,
    // y vuelve a escuchar hasta que el usuario lo detiene.
    private var voiceMode = false
    private var silentAttempts = 0

    private val backgroundColorValue = Color.rgb(18, 18, 24)
    private val green = Color.rgb(120, 220, 170)
    private val white = Color.WHITE
    private val muted = Color.LTGRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AriDatabase(this)
        ceo = AriCeo(database)
        ceo.hire(MemoryAgent(database) { memoriesPending = true })
        ceo.hire(ClockAgent())
        buildInterface()
        voice = VoiceController(this, voiceListener)
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

        memoryButton = Button(this).apply {
            text = "Memoria"
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
            hint = "Escribe un mensaje..."
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

        voiceButton = Button(this).apply {
            text = VOICE_START_LABEL
            setOnClickListener { toggleVoiceMode() }
        }

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                sendButton,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                voiceButton,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
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
        root.addView(bottomRow)

        setContentView(root)
    }

    private fun restoreHistory() {
        val history = database.recentMessages(HISTORY_DISPLAY_LIMIT)
        if (history.isEmpty()) return

        chat.text = ""
        history.forEach { message ->
            val speaker = if (message.role == ROLE_USER) "Tú" else "Ari"
            chat.append("\n\n$speaker: ${message.content}")
        }
        scrollToBottom()
    }

    private fun loadSavedModel() {
        val modelFile = File(filesDir, MODEL_FILE_NAME)
        if (!modelFile.exists() || modelFile.length() == 0L) return

        status.text = "Cargando el modelo guardado..."
        selectButton.isEnabled = false
        executor.execute { initializeModel(modelFile) }
    }

    private fun scrollToBottom() {
        scroll.post { scroll.scrollTo(0, chat.bottom) }
    }

    private fun appendChat(text: String) {
        chat.append(text)
        scrollToBottom()
    }

    private fun selectModel() {
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

        executor.execute {
            try {
                val modelFile = File(filesDir, MODEL_FILE_NAME)
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
                }
            }
        }
    }

    private fun initializeModel(modelFile: File) {
        try {
            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir.absolutePath
                )
            )

            newEngine.initialize()
            val newConversation = newEngine.createConversation()

            engine = newEngine
            conversation = newConversation
            modelReady = true

            runOnUiThread {
                historyPending = true
                memoriesPending = true
                status.text = "Gemma cargado. Ari está listo."
                appendChat("\n\nAri: ¡Hola! Ya estoy listo para conversar.")
                selectButton.isEnabled = true
            }
        } catch (e: Exception) {
            runOnUiThread {
                status.text =
                    "No se pudo iniciar el modelo: ${e.message}"
                selectButton.isEnabled = true
            }
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        if (text.startsWith("/")) {
            input.text.clear()
            val reply = handleCommand(text)
            if (voiceMode) voice.speak(reply)
            return
        }

        if (busy) return

        ceo.route(text)?.let { delegation ->
            input.text.clear()
            val result = ceo.run(delegation)
            database.addMessage(ROLE_USER, text)
            database.addMessage(ROLE_ARI, result.reply)
            appendChat("\n\nTú: $text\n\nAri (${delegation.agent.name}): ${result.reply}")
            if (voiceMode) voice.speak(result.reply)
            return
        }

        val activeConversation = conversation
        if (!modelReady || activeConversation == null) {
            status.text = "El modelo aún no está listo. Selecciona o espera a que cargue."
            if (voiceMode) {
                stopVoiceMode(status.text.toString())
                voice.speak("El modelo aún no está listo.")
            }
            return
        }

        val prompt = buildPrompt(text)
        database.addMessage(ROLE_USER, text)

        input.text.clear()
        appendChat("\n\nTú: $text\n\nAri: ")
        status.text = "Ari está pensando..."
        busy = true
        sendButton.isEnabled = false

        val response = StringBuilder()

        activeConversation.sendMessageAsync(
            prompt,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val chunk = message.toString()
                    runOnUiThread {
                        response.append(chunk)
                        appendChat(chunk)
                    }
                }

                override fun onDone() {
                    runOnUiThread {
                        saveResponse(response)
                        busy = false
                        sendButton.isEnabled = true
                        if (voiceMode) {
                            status.text = "Ari está hablando..."
                            voice.speak(response.toString())
                        } else {
                            status.text = "Gemma listo."
                        }
                    }
                }

                override fun onError(throwable: Throwable) {
                    runOnUiThread {
                        saveResponse(response)
                        busy = false
                        status.text =
                            "Error de respuesta: ${throwable.message}"
                        appendChat("\n[Error al generar la respuesta]")
                        sendButton.isEnabled = true
                        if (voiceMode) voice.speak("Hubo un error al generar la respuesta.")
                    }
                }
            }
        )
    }

    private fun saveResponse(response: StringBuilder) {
        val content = response.toString().trim()
        if (content.isNotEmpty()) {
            database.addMessage(ROLE_ARI, content)
        }
    }

    /**
     * Añade al mensaje el contexto que el modelo aún no conoce: los
     * recuerdos (si cambiaron) y, en el primer mensaje tras cargar el
     * modelo, las últimas conversaciones guardadas.
     */
    private fun buildPrompt(text: String): String {
        val context = StringBuilder()

        if (memoriesPending) {
            val memories = database.memories()
            if (memories.isNotEmpty()) {
                context.append("Recuerdos guardados sobre el usuario:\n")
                memories.forEach { context.append("- ${it.content}\n") }
                context.append("\n")
            }
        }

        if (historyPending) {
            val history = database.recentMessages(HISTORY_CONTEXT_LIMIT)
            if (history.isNotEmpty()) {
                context.append("Últimos mensajes de conversaciones anteriores:\n")
                history.forEach { message ->
                    val speaker = if (message.role == ROLE_USER) "Usuario" else "Ari"
                    context.append("$speaker: ${message.content.take(HISTORY_CONTEXT_CHARS)}\n")
                }
                context.append("\n")
            }
        }

        memoriesPending = false
        historyPending = false

        if (context.isEmpty()) return text

        return "[Contexto privado de Ari. Úsalo para responder, pero no lo repitas literalmente.]\n" +
            context +
            "Mensaje del usuario: $text"
    }

    /** Ejecuta un comando local, muestra la respuesta y la devuelve. */
    private fun handleCommand(command: String): String {
        val name = command.substringBefore(" ").lowercase()
        val argument = command.substringAfter(" ", "").trim()

        val reply = when (name) {
            "/recuerda" -> {
                if (argument.isEmpty()) {
                    "Escribe qué quieres que recuerde, por ejemplo: /recuerda Me gusta el café."
                } else {
                    addMemory(argument)
                    "Lo recordaré: $argument"
                }
            }

            "/memoria" -> {
                val memories = database.memories()
                if (memories.isEmpty()) {
                    "Aún no tengo recuerdos guardados."
                } else {
                    "Esto es lo que recuerdo:" +
                        memories.joinToString("") { "\n• ${it.content}" }
                }
            }

            "/olvida" -> {
                if (argument.isEmpty()) {
                    "Escribe qué debo olvidar, por ejemplo: /olvida café."
                } else {
                    val deleted = database.deleteMemoriesMatching(argument)
                    if (deleted > 0) memoriesPending = true
                    when (deleted) {
                        0 -> "No encontré recuerdos que contengan \"$argument\"."
                        1 -> "Olvidé 1 recuerdo."
                        else -> "Olvidé $deleted recuerdos."
                    }
                }
            }

            "/agentes" -> ceo.report()

            "/delega" -> {
                val agentId = argument.substringBefore(" ")
                val request = argument.substringAfter(" ", "").trim()
                val agent = ceo.agent(agentId)
                when {
                    argument.isEmpty() || request.isEmpty() ->
                        "Escribe el agente y la tarea, por ejemplo: /delega reloj qué hora es."
                    agent == null ->
                        "No tengo un agente llamado \"$agentId\". Usa /agentes para ver el equipo."
                    else -> "${agent.name}: ${ceo.delegateTo(agent, request).reply}"
                }
            }

            else -> "No conozco ese comando. Usa /recuerda <texto>, /memoria, /olvida <texto>, " +
                "/agentes o /delega <agente> <tarea>."
        }

        appendChat("\n\nTú: $command\n\nAri: $reply")
        return reply
    }

    private fun toggleVoiceMode() {
        if (voiceMode) {
            stopVoiceMode("Modo voz detenido.")
            return
        }

        if (!voice.isRecognitionAvailable) {
            status.text = "Este teléfono no tiene un servicio de reconocimiento de voz disponible."
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }

        startVoiceMode()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_AUDIO) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoiceMode()
        } else {
            status.text = "Ari necesita permiso de micrófono para el modo voz."
        }
    }

    private fun startVoiceMode() {
        voiceMode = true
        silentAttempts = 0
        voiceButton.text = VOICE_STOP_LABEL
        if (busy) {
            // Cuando termine la respuesta en curso, Ari la leerá y escuchará.
            status.text = "Modo voz activo. Esperando la respuesta de Ari..."
        } else {
            listen()
        }
    }

    private fun stopVoiceMode(message: String) {
        voiceMode = false
        voice.stopAll()
        voiceButton.text = VOICE_START_LABEL
        status.text = message
    }

    private fun listen() {
        status.text = "Preparando el micrófono..."
        voice.startListening()
    }

    private val voiceListener = object : VoiceController.Listener {
        override fun onListeningStarted() {
            if (voiceMode) status.text = "Escuchando... habla ahora."
        }

        override fun onPartialSpeech(text: String) {
            if (voiceMode) input.setText(text)
        }

        override fun onSpeechResult(text: String) {
            if (!voiceMode) return
            silentAttempts = 0
            input.setText(text)
            sendMessage()
        }

        override fun onSpeechError(message: String, noSpeech: Boolean) {
            if (!voiceMode) return

            if (noSpeech && ++silentAttempts < MAX_SILENT_ATTEMPTS) {
                listen()
            } else if (noSpeech) {
                stopVoiceMode("Modo voz pausado: no te escuché. Toca \"$VOICE_START_LABEL\" para seguir.")
            } else {
                stopVoiceMode(message)
            }
        }

        override fun onSpeakingFinished() {
            if (voiceMode && !busy) listen()
        }
    }

    override fun onPause() {
        super.onPause()
        if (voiceMode) stopVoiceMode("Modo voz detenido.")
    }

    private fun addMemory(content: String) {
        database.addMemory(content)
        memoriesPending = true
    }

    private fun showMemoriesDialog() {
        val memories = database.memories()
        val builder = AlertDialog.Builder(this)
            .setTitle("Recuerdos de Ari")
            .setPositiveButton("Agregar") { _, _ -> showAddMemoryDialog() }
            .setNegativeButton("Cerrar", null)

        if (memories.isEmpty()) {
            builder.setMessage("Aún no hay recuerdos. Agrega uno o usa /recuerda <texto>.")
        } else {
            val items = memories.map { it.content }.toTypedArray()
            builder.setItems(items) { _, which ->
                confirmDeleteMemory(memories[which])
            }
        }

        builder.show()
    }

    private fun showAddMemoryDialog() {
        val field = EditText(this).apply {
            hint = "Algo que Ari debe recordar"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        AlertDialog.Builder(this)
            .setTitle("Nuevo recuerdo")
            .setView(field)
            .setPositiveButton("Guardar") { _, _ ->
                val content = field.text.toString().trim()
                if (content.isNotEmpty()) {
                    addMemory(content)
                    status.text = "Recuerdo guardado."
                }
                showMemoriesDialog()
            }
            .setNegativeButton("Cancelar") { _, _ -> showMemoriesDialog() }
            .show()
    }

    private fun confirmDeleteMemory(memory: Memory) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar este recuerdo?")
            .setMessage(memory.content)
            .setPositiveButton("Borrar") { _, _ ->
                database.deleteMemory(memory.id)
                memoriesPending = true
                status.text = "Recuerdo borrado."
                showMemoriesDialog()
            }
            .setNegativeButton("Cancelar") { _, _ -> showMemoriesDialog() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.execute {
            try {
                conversation?.close()
            } catch (_: Exception) {
            }

            try {
                engine?.close()
            } catch (_: Exception) {
            }
        }

        executor.shutdown()
        voice.release()
        database.close()
    }

    companion object {
        private const val PICK_MODEL = 101
        private const val REQUEST_AUDIO = 102
        private const val MAX_SILENT_ATTEMPTS = 3
        private const val VOICE_START_LABEL = "🎤 Voz"
        private const val VOICE_STOP_LABEL = "⏹ Detener"
        private const val MODEL_FILE_NAME = "gemma-model.litertlm"
        private const val ROLE_USER = "user"
        private const val ROLE_ARI = "ari"
        private const val HISTORY_DISPLAY_LIMIT = 100
        private const val HISTORY_CONTEXT_LIMIT = 8
        private const val HISTORY_CONTEXT_CHARS = 300
    }
}
