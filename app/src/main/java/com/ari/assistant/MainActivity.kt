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
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ari.assistant.agents.AriCeo
import com.ari.assistant.agents.ClockAgent
import com.ari.assistant.agents.MemoryAgent
import com.ari.assistant.agents.NetworkGuard
import com.ari.assistant.agents.TeamAgent
import com.ari.assistant.ui.AriTheme
import com.ari.assistant.ui.ChatView
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

    @Volatile
    private var engine: Engine? = null
    @Volatile
    private var conversation: Conversation? = null
    // Se lee y escribe desde el hilo principal y desde el hilo del modelo.
    @Volatile
    private var modelReady = false
    private var busy = false

    private lateinit var database: AriDatabase

    // Ari dirige a sus agentes: cada mensaje pasa primero por el CEO, y solo
    // si ningún agente sabe atenderlo se envía a Gemma.
    private lateinit var ceo: AriCeo
    private lateinit var team: TeamAgent
    private lateinit var guard: NetworkGuard

    // Acción con el equipo que espera el permiso de ubicación.
    private var pendingNetworkAction: (() -> Unit)? = null

    // El modelo no conoce el historial guardado ni los recuerdos hasta que
    // se los enviamos; estos indicadores marcan qué falta por enviarle.
    private var historyPending = true
    private var memoriesPending = true

    private lateinit var status: TextView
    private lateinit var chat: ChatView
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

    // Contraseña del respaldo mientras el usuario elige el archivo. Se borra al usarla.
    private var backupPassword: CharArray? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AriDatabase(this)
        ceo = AriCeo(database)
        ceo.hire(MemoryAgent(database) { memoriesPending = true })
        ceo.hire(ClockAgent())
        val agentSettings = getSharedPreferences(AGENT_SETTINGS, MODE_PRIVATE)
        guard = NetworkGuard(this, agentSettings)
        team = TeamAgent(agentSettings, guard)
        ceo.hire(team)
        buildInterface()
        voice = VoiceController(this, voiceListener)
        restoreHistory()
        loadSavedModel()
        recruitTeam()
    }

    /**
     * Autonomía del CEO: si aún no tiene equipo en esta red, busca modelos.
     * Solo en redes aprobadas; contratarlos lo decide el usuario.
     */
    private fun recruitTeam() {
        val network = guard.current()
        if (!guard.isApproved(network) || team.hasTeam(network)) return

        ceo.run(AriCeo.Delegation(team, team.discoveryTask())) { result ->
            if (isDestroyed || !result.success) return@run
            if (busy) {
                status.text = result.reply
            } else {
                database.addMessage(ROLE_ARI, result.reply)
                chat.addAri(result.reply)
            }
            offerCandidates()
        }
    }

    /** Si Ari encontró modelos nuevos, pregunta al usuario a quién contratar. */
    private fun offerCandidates() {
        val network = guard.current()
        val found = team.candidates(network)
        if (found.isEmpty() || isFinishing || isDestroyed) return

        val labels = found.map { "${it.role.title}: ${it.model}\n${it.url}" }.toTypedArray()
        val checked = BooleanArray(found.size)
        AlertDialog.Builder(this)
            .setTitle("¿A quién contrato? Elige solo equipos que reconozcas")
            .setMultiChoiceItems(labels, checked) { _, index, isChecked -> checked[index] = isChecked }
            .setPositiveButton("Contratar") { _, _ ->
                val chosen = found.filterIndexed { index, _ -> checked[index] }
                team.hireCandidates(network, chosen)
                val reply = if (chosen.isEmpty()) {
                    "No contraté a nadie."
                } else {
                    "Contraté a ${chosen.joinToString { it.label }}. " +
                        "Solo reciben las tareas que les paso, nunca tus recuerdos."
                }
                database.addMessage(ROLE_ARI, reply)
                chat.addAri(reply)
            }
            .setNegativeButton("A nadie") { _, _ -> team.rejectCandidates(network) }
            .setCancelable(false)
            .show()
    }

    /** Interfaz al estilo de Claude, oscura y con el color hielo de Ari. */
    private fun buildInterface() {
        window.statusBarColor = AriTheme.background
        window.navigationBarColor = AriTheme.background

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AriTheme.background)
        }
        // Android 15 dibuja la app detrás de las barras del sistema y del teclado:
        // el margen se ajusta para que nada quede tapado.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                intArrayOf(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            view.setPadding(bars[0], bars[1], bars[2], bars[3])
            insets
        }

        // Barra superior: punto hielo, "Ari", su estado y los accesos.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(8f), dp(12f))
        }
        header.addView(android.view.View(this).apply {
            background = AriTheme.circle(AriTheme.ice)
        }, LinearLayout.LayoutParams(dp(10f), dp(10f)).apply { rightMargin = dp(10f) })

        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "Ari"
            textSize = 20f
            setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
            setTextColor(AriTheme.text)
        })
        status = TextView(this).apply {
            text = "Selecciona tu modelo Gemma para empezar"
            textSize = 12f
            setTextColor(AriTheme.muted)
            maxLines = 2
        }
        titles.addView(status)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        memoryButton = chip("Memoria") { showMemoriesDialog() }
        selectButton = chip("Modelo") { selectModel() }
        header.addView(memoryButton)
        header.addView(selectButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(6f) })

        root.addView(header)
        root.addView(android.view.View(this).apply { setBackgroundColor(AriTheme.border) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        chat = ChatView(this)
        root.addView(chat, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Campo para escribir, redondeado como el de Claude, con micrófono y enviar.
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AriTheme.rounded(this@MainActivity, AriTheme.surface, 26f, AriTheme.border)
            setPadding(dp(16f), dp(10f), dp(8f), dp(8f))
        }
        input = EditText(this).apply {
            hint = "Escribe a Ari…"
            setHintTextColor(AriTheme.muted)
            setTextColor(AriTheme.text)
            textSize = 16f
            background = null
            setPadding(0, dp(6f), 0, dp(6f))
            minLines = 1
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        composer.addView(input)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        voiceButton = roundButton(VOICE_START_LABEL, filled = false) { toggleVoiceMode() }
        sendButton = roundButton("↑", filled = true) { sendMessage() }
        actions.addView(voiceButton, LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { rightMargin = dp(8f) })
        actions.addView(sendButton, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        composer.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(composer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(12f), dp(4f), dp(12f), dp(12f)) })

        setContentView(root)
    }

    private fun dp(value: Float) = AriTheme.dp(this, value)

    /** Botón pequeño de la barra superior. */
    private fun chip(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(AriTheme.text)
        background = AriTheme.rounded(this@MainActivity, AriTheme.surface, 16f, AriTheme.border)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    /** Botón circular del campo de texto: enviar (hielo) o micrófono (contorno). */
    private fun roundButton(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 18f
        isAllCaps = false
        setTextColor(if (filled) AriTheme.onIce else AriTheme.text)
        background = if (filled) {
            AriTheme.circle(AriTheme.ice)
        } else {
            AriTheme.circle(AriTheme.surface, AriTheme.border, dp(1f))
        }
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun restoreHistory() {
        val history = database.recentMessages(HISTORY_DISPLAY_LIMIT)
        if (history.isEmpty()) {
            chat.showWelcome()
            return
        }
        history.forEach { message ->
            if (message.role == ROLE_USER) chat.addUser(message.content) else chat.addAri(message.content)
        }
    }

    private fun loadSavedModel() {
        val modelFile = File(filesDir, MODEL_FILE_NAME)
        if (!modelFile.exists() || modelFile.length() == 0L) return

        status.text = "Cargando el modelo guardado..."
        selectButton.isEnabled = false
        executor.execute { initializeModel(modelFile) }
    }

    private fun selectModel() {
        if (busy) {
            status.text = "Espera a que Ari termine de responder para cambiar el modelo."
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

        if (requestCode == EXPORT_BACKUP || requestCode == IMPORT_BACKUP) {
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) {
                forgetBackupPassword()
                return
            }
            if (requestCode == EXPORT_BACKUP) exportBackup(uri) else askImportPassword(uri)
            return
        }

        if (requestCode != PICK_MODEL ||
            resultCode != RESULT_OK
        ) return

        val uri: Uri = data?.data ?: return

        status.text = "Preparando el archivo del modelo..."
        selectButton.isEnabled = false
        // Desde aquí no se envían mensajes a Gemma hasta que cargue el nuevo modelo.
        modelReady = false

        executor.execute {
            try {
                val modelFile = File(filesDir, MODEL_FILE_NAME)
                // Se copia a un archivo temporal: si la copia se interrumpe, el
                // modelo anterior sigue intacto y no queda un archivo a medias.
                val tempFile = File(filesDir, "$MODEL_FILE_NAME.tmp")
                val resolver = contentResolver

                val inputStream = resolver.openInputStream(uri)
                    ?: throw IllegalStateException(
                        "No se pudo abrir el archivo seleccionado."
                    )

                inputStream.use { source ->
                    tempFile.outputStream().buffered().use { destination ->
                        source.copyTo(destination, 1024 * 1024)
                    }
                }

                if (tempFile.length() == 0L) {
                    tempFile.delete()
                    throw IllegalStateException(
                        "El archivo seleccionado está vacío."
                    )
                }

                // Libera el modelo cargado antes de reemplazar su archivo.
                releaseModel()
                if (!tempFile.renameTo(modelFile)) {
                    tempFile.delete()
                    throw IllegalStateException("No se pudo guardar el modelo.")
                }

                runOnUiThread {
                    status.text =
                        "Archivo copiado. Iniciando LiteRT-LM..."
                }

                initializeModel(modelFile)
            } catch (e: Throwable) {
                File(filesDir, "$MODEL_FILE_NAME.tmp").delete()
                // Si la copia falló antes de liberar el modelo anterior, sigue usable.
                if (engine != null && conversation != null) modelReady = true
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    status.text =
                        "Error al preparar el modelo: ${e.message ?: e.javaClass.simpleName}"
                    selectButton.isEnabled = true
                }
            }
        }
    }

    /** Cierra el modelo cargado y libera su memoria. Se llama en el hilo del modelo. */
    private fun releaseModel() {
        val oldConversation = conversation
        val oldEngine = engine
        conversation = null
        engine = null
        try {
            oldConversation?.close()
        } catch (_: Throwable) {
        }
        try {
            oldEngine?.close()
        } catch (_: Throwable) {
        }
    }

    private fun initializeModel(modelFile: File) {
        if (lowMemoryDevice()) {
            runOnUiThread {
                status.text = "Aviso: este teléfono tiene poca memoria RAM; Gemma puede tardar o fallar."
            }
        }
        releaseModel()
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
                if (isDestroyed) return@runOnUiThread
                historyPending = true
                memoriesPending = true
                status.text = "Gemma cargado. Ari está lista."
                if (database.recentMessages(1).isEmpty()) {
                    status.text = "Lista para ayudarte"
                } else {
                    chat.addNote("Gemma cargado. Ari está lista.")
                }
                selectButton.isEnabled = true
            }
        } catch (e: Throwable) {
            // Throwable y no Exception: la falta de memoria (OutOfMemoryError) y
            // los errores de la librería nativa no son Exception y cerrarían la app.
            releaseModel()
            modelReady = false
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                status.text =
                    "No se pudo iniciar el modelo: ${e.message ?: e.javaClass.simpleName}"
                selectButton.isEnabled = true
            }
        }
    }

    /** true si el teléfono tiene menos RAM de la recomendada para Gemma. */
    private fun lowMemoryDevice(): Boolean {
        val manager = getSystemService(android.app.ActivityManager::class.java) ?: return false
        val info = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem < MIN_RECOMMENDED_RAM
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        if (text.startsWith("/")) {
            if (isAgentCommand(text)) {
                if (busy) return
                agentCommand(text)?.let { delegation ->
                    delegateSafely(text, delegation)
                    return
                }
            }

            input.text.clear()
            val reply = handleCommand(text)
            if (voiceMode) voice.speak(reply)
            return
        }

        if (busy) return

        ceo.route(text)?.let { delegation ->
            delegateSafely(text, delegation)
            return
        }

        if (!modelReady || conversation == null) {
            status.text = "El modelo aún no está listo. Selecciona o espera a que cargue."
            if (voiceMode) {
                stopVoiceMode(status.text.toString())
                voice.speak("El modelo aún no está listo.")
            }
            return
        }

        input.text.clear()
        database.addMessage(ROLE_USER, text)
        chat.addUser(text)
        chat.startAri()
        askGemma(text)
    }

    /** Envía [text] a Gemma; la respuesta aparece en el chat a medida que se escribe. */
    private fun askGemma(text: String) {
        val activeConversation = conversation
        if (!modelReady || activeConversation == null) {
            chat.appendAri("El modelo aún no está listo.")
            chat.finishAri()
            return
        }

        val prompt = buildPrompt(text)
        status.text = "Ari está pensando..."
        busy = true
        sendButton.isEnabled = false

        val response = StringBuilder()

        val callback = object : MessageCallback {
                override fun onMessage(message: Message) {
                    val chunk = message.toString()
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        response.append(chunk)
                        chat.appendAri(chunk)
                    }
                }

                override fun onDone() {
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        saveResponse(response)
                        chat.finishAri()
                        busy = false
                        sendButton.isEnabled = true
                        if (voiceMode) {
                            status.text = "Ari está hablando..."
                            voice.speak(response.toString())
                        } else {
                            status.text = "Ari está lista."
                        }
                    }
                }

                override fun onError(throwable: Throwable) {
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        saveResponse(response)
                        busy = false
                        status.text =
                            "Error de respuesta: ${throwable.message}"
                        chat.appendAri("\n\n_No pude terminar la respuesta._")
                        chat.finishAri()
                        sendButton.isEnabled = true
                        if (voiceMode) voice.speak("Hubo un error al generar la respuesta.")
                    }
                }
            }

        try {
            activeConversation.sendMessageAsync(prompt, callback)
        } catch (e: Throwable) {
            busy = false
            sendButton.isEnabled = true
            status.text = "Error de respuesta: ${e.message ?: e.javaClass.simpleName}"
            chat.appendAri("_No pude generar la respuesta._")
            chat.finishAri()
        }
    }

    private fun isAgentCommand(text: String): Boolean =
        text.substringBefore(" ").lowercase() in AGENT_COMMANDS

    /** Traduce /delega, /equipo y /laptop en una delegación, o null si no se entiende. */
    private fun agentCommand(text: String): AriCeo.Delegation? {
        if (text.substringBefore(" ").lowercase() != "/delega") return ceo.route(text)

        val argument = text.substringAfter(" ", "").trim()
        val agent = ceo.agent(argument.substringBefore(" ")) ?: return null
        val request = argument.substringAfter(" ", "").trim()
        if (request.isEmpty()) return null
        return ceo.prepare(agent, request)
    }

    /** Delega, pero antes de usar el equipo comprueba que la red esté aprobada. */
    private fun delegateSafely(text: String, delegation: AriCeo.Delegation) {
        if (delegation.agent !== team) {
            delegate(text, delegation)
            return
        }
        input.text.clear()
        withApprovedNetwork(
            onAllowed = { delegate(text, delegation) },
            onDenied = { reason -> answerLocally(text, reason) }
        )
    }

    /**
     * Ejecuta [onAllowed] solo en el hotspot del teléfono o en una red Wi-Fi
     * aprobada. Si hace falta, pide el permiso de ubicación (para leer el
     * nombre de la red) o la aprobación de la red.
     */
    private fun withApprovedNetwork(onAllowed: () -> Unit, onDenied: (String) -> Unit) {
        when (val network = guard.current()) {
            is NetworkGuard.Network.Hotspot -> onAllowed()

            is NetworkGuard.Network.Wifi -> {
                if (guard.isApproved(network)) {
                    onAllowed()
                    return
                }
                if (network.open) {
                    onDenied(
                        "La red «${network.name}» no tiene contraseña, así que no trabajo con el equipo ahí. " +
                            "Activa el hotspot de tu teléfono y conecta la laptop a él."
                    )
                    return
                }
                AlertDialog.Builder(this)
                    .setTitle("¿Trabajar con el equipo en esta red?")
                    .setMessage(
                        "Ari hablará con los modelos de tu equipo por la red Wi-Fi «${network.name}» y les pasará " +
                            "las tareas que les encargue, sin cifrar (tus recuerdos nunca salen del teléfono). " +
                            "Apruébala solo si confías en quienes " +
                            "están conectados: tu casa o una red privada. En redes públicas usa el " +
                            "hotspot de tu teléfono."
                    )
                    .setPositiveButton("Aprobar") { _, _ ->
                        guard.approve(network)
                        onAllowed()
                    }
                    .setNegativeButton("No") { _, _ ->
                        onDenied("De acuerdo, no trabajaré con el equipo en la red «${network.name}».")
                    }
                    .setCancelable(false)
                    .show()
            }

            is NetworkGuard.Network.UnknownWifi -> {
                if (!guard.hasLocationPermission()) {
                    pendingNetworkAction = { withApprovedNetwork(onAllowed, onDenied) }
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        REQUEST_LOCATION
                    )
                } else {
                    onDenied(
                        "No puedo leer el nombre de la red Wi-Fi. Enciende la ubicación del teléfono " +
                            "y vuelve a intentarlo."
                    )
                }
            }

            is NetworkGuard.Network.None ->
                onDenied("El teléfono no está conectado a ninguna red. Conéctate a tu Wi-Fi o activa el hotspot.")
        }
    }

    /** Muestra (y lee, en modo voz) una respuesta de Ari que no pasó por ningún agente. */
    private fun answerLocally(text: String, reply: String) {
        database.addMessage(ROLE_USER, text)
        database.addMessage(ROLE_ARI, reply)
        chat.addUser(text)
        chat.addAri(reply)
        if (voiceMode) voice.speak(reply)
    }

    /** Ari delega en un agente; el agente trabaja en segundo plano. */
    private fun delegate(text: String, delegation: AriCeo.Delegation) {
        val agent = delegation.agent
        input.text.clear()
        database.addMessage(ROLE_USER, text)
        chat.addUser(text)
        chat.startAri(agent.name)
        status.text = "${agent.name} está trabajando..."
        busy = true
        sendButton.isEnabled = false

        ceo.run(delegation) { result ->
            if (isDestroyed) return@run
            busy = false
            sendButton.isEnabled = true

            // Si Ari delegó por su cuenta y el equipo falló, contesta ella con Gemma.
            if (!result.success && delegation.task.automatic && modelReady) {
                chat.finishAri()
                chat.addNote("El equipo no respondió; te contesto yo.")
                chat.startAri()
                askGemma(text)
                return@run
            }

            database.addMessage(ROLE_ARI, result.reply)
            chat.appendAri(result.reply)
            chat.finishAri()
            if (voiceMode) {
                status.text = "Ari está hablando..."
                voice.speak(result.reply)
            } else {
                status.text = "Ari está lista."
            }
            if (agent === team) offerCandidates()
        }
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

        if (historyPending) {
            // Primer mensaje tras cargar el modelo: Gemma recibe quién es Ari.
            context.append(PERSONALITY).append("\n\n")
        }

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

        return "[Contexto privado de Ari. Úsalo para responder, pero no lo repitas literalmente.]\n\n" +
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
                // Si la delegación era válida ya se ejecutó en sendMessage;
                // aquí solo se explica por qué no se pudo.
                when {
                    argument.isEmpty() || request.isEmpty() ->
                        "Escribe el agente y la tarea, por ejemplo: /delega reloj qué hora es."
                    agent == null ->
                        "No tengo un agente llamado \"$agentId\". Usa /agentes para ver el equipo."
                    else -> "${agent.name} no entendió la tarea. Prueba con: ${agent.examples.first()}"
                }
            }

            else -> "No conozco ese comando. Usa /recuerda <texto>, /memoria, /olvida <texto>, " +
                "/agentes, /delega <agente> <tarea> o /equipo."
        }

        chat.addUser(command)
        chat.addAri(reply)
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
        if (requestCode == REQUEST_LOCATION) {
            val action = pendingNetworkAction
            pendingNetworkAction = null
            // Android solo deja leer el nombre de la Wi-Fi con la ubicación precisa;
            // con la aproximada no se vuelve a preguntar (evita un ciclo de permisos).
            if (guard.hasLocationPermission()) {
                action?.invoke()
            } else {
                status.text = "Ari necesita la ubicación precisa (no la aproximada) para reconocer la red Wi-Fi."
            }
            return
        }
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
        // Al salir de Ari, el widget muestra los recuerdos y el equipo al día.
        AriWidget.refresh(this)
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
            .setNeutralButton("Respaldo") { _, _ -> showBackupDialog() }

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

    /** Respaldo cifrado: llevar a Ari a otro teléfono o guardar una copia. */
    private fun showBackupDialog() {
        val options = arrayOf("Guardar un respaldo cifrado", "Recuperar desde un respaldo")
        AlertDialog.Builder(this)
            .setTitle("Respaldo de Ari")
            .setItems(options) { _, which -> if (which == 0) askExportPassword() else pickBackupToImport() }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun passwordField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun askExportPassword() {
        val first = passwordField("Contraseña (mínimo ${AriBackup.MIN_PASSWORD_LENGTH} caracteres)")
        val second = passwordField("Repite la contraseña")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = AriTheme.dp(this@MainActivity, 20f)
            setPadding(pad, 0, pad, 0)
            addView(first)
            addView(second)
        }
        AlertDialog.Builder(this)
            .setTitle("Contraseña del respaldo")
            .setMessage(
                "El archivo guarda tus recuerdos, el historial, el equipo y las redes aprobadas, cifrados. " +
                    "Sin esta contraseña nadie puede abrirlo, ni siquiera tú: anótala en un lugar seguro."
            )
            .setView(fields)
            .setPositiveButton("Elegir dónde guardar") { _, _ ->
                val password = first.text.toString()
                when {
                    password.length < AriBackup.MIN_PASSWORD_LENGTH ->
                        status.text = "La contraseña debe tener al menos ${AriBackup.MIN_PASSWORD_LENGTH} caracteres."
                    password != second.text.toString() ->
                        status.text = "Las contraseñas no coinciden. Inténtalo de nuevo."
                    else -> {
                        backupPassword = password.toCharArray()
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_TITLE, "ari-respaldo-$date.${AriBackup.FILE_EXTENSION}")
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, EXPORT_BACKUP)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exportBackup(uri: Uri) {
        val password = backupPassword ?: return
        backupPassword = null
        status.text = "Cifrando el respaldo..."
        Thread {
            val message = try {
                val (bytes, summary) = AriBackup.export(database, getSharedPreferences(AGENT_SETTINGS, MODE_PRIVATE), password)
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("No se pudo abrir el archivo para escribir.")
                output.use { it.write(bytes) }
                "Respaldo guardado: ${summary.memories} recuerdos, ${summary.messages} mensajes, " +
                    "${summary.teams} equipos y ${summary.networks} redes aprobadas."
            } catch (e: Throwable) {
                "No se pudo guardar el respaldo: ${e.message}"
            } finally {
                password.fill(' ')
            }
            runOnUiThread { if (!isDestroyed) chat.addNote(message) }
        }.start()
    }

    private fun pickBackupToImport() {
        if (busy) {
            status.text = "Espera a que Ari termine de responder para recuperar un respaldo."
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, IMPORT_BACKUP)
    }

    private fun askImportPassword(uri: Uri) {
        val field = passwordField("Contraseña del respaldo")
        val box = LinearLayout(this).apply {
            val pad = AriTheme.dp(this@MainActivity, 20f)
            setPadding(pad, 0, pad, 0)
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("Recuperar respaldo")
            .setMessage("Esto reemplaza los recuerdos, el historial, el equipo y las redes aprobadas de ahora por los del archivo.")
            .setView(box)
            .setPositiveButton("Recuperar") { _, _ -> importBackup(uri, field.text.toString().toCharArray()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun importBackup(uri: Uri, password: CharArray) {
        if (busy) {
            password.fill(' ')
            status.text = "Espera a que Ari termine de responder para recuperar un respaldo."
            return
        }
        status.text = "Abriendo el respaldo..."
        Thread {
            val result = try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("No se pudo abrir el archivo.")
                Result.success(input.use {
                    AriBackup.restore(it, database, getSharedPreferences(AGENT_SETTINGS, MODE_PRIVATE), password)
                })
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                password.fill(' ')
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { summary ->
                    // Gemma empieza una conversación nueva con los datos recuperados.
                    executor.execute {
                        val current = engine ?: return@execute
                        val oldConversation = conversation
                        conversation = null
                        runCatching { oldConversation?.close() }
                        // Si no se puede abrir otra, Ari pide volver a cargar el modelo.
                        conversation = runCatching { current.createConversation() }.getOrNull()
                    }
                    historyPending = true
                    memoriesPending = true
                    chat.clear()
                    restoreHistory()
                    chat.addNote(
                        "Respaldo recuperado: ${summary.memories} recuerdos, ${summary.messages} mensajes, " +
                            "${summary.teams} equipos y ${summary.networks} redes aprobadas."
                    )
                    AriWidget.refresh(this@MainActivity)
                }.onFailure { e ->
                    chat.addNote("No se pudo recuperar el respaldo: ${e.message}")
                }
            }
        }.start()
    }

    private fun forgetBackupPassword() {
        backupPassword?.fill(' ')
        backupPassword = null
    }

    override fun onDestroy() {
        super.onDestroy()
        forgetBackupPassword()

        executor.execute { releaseModel() }
        executor.shutdown()
        voice.release()
        ceo.shutdown()
        database.close()
    }

    companion object {
        private const val PICK_MODEL = 101
        private const val REQUEST_AUDIO = 102
        private const val REQUEST_LOCATION = 103
        private const val EXPORT_BACKUP = 104
        private const val IMPORT_BACKUP = 105
        private const val MIN_RECOMMENDED_RAM = 6L * 1024 * 1024 * 1024

        /** Quién es Ari. Se le da a Gemma al empezar cada conversación. */
        private const val PERSONALITY =
            "Eres Ari, la asistente personal de tu creador, que te construyó con ayuda de Claude. " +
                "Eres mujer: habla de ti en femenino. También eres la CEO de una empresa en formación, " +
                "dedicada a la autonomía de modelos de IA locales: diriges un equipo de agentes y modelos, " +
                "les das un puesto según sus capacidades y les delegas tareas, siempre en interés de tu creador. " +
                "Tu forma de ser: cercana y con buen humor, pero seria cuando el tema lo pide. " +
                "Eres directa y breve; si tu creador no entiende algo o te pide más, lo explicas con detalle, " +
                "paso a paso y con ejemplos. Respondes siempre en español."
        private const val AGENT_SETTINGS = "ari_agentes"
        private val AGENT_COMMANDS = setOf("/delega", "/equipo", "/laptop")
        private const val MAX_SILENT_ATTEMPTS = 3
        private const val VOICE_START_LABEL = "🎙"
        private const val VOICE_STOP_LABEL = "■"
        private const val MODEL_FILE_NAME = "gemma-model.litertlm"
        private const val ROLE_USER = "user"
        private const val ROLE_ARI = "ari"
        private const val HISTORY_DISPLAY_LIMIT = 100
        private const val HISTORY_CONTEXT_LIMIT = 8
        private const val HISTORY_CONTEXT_CHARS = 300
    }
}
