package com.ari.assistant

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    private lateinit var status: TextView
    private lateinit var chat: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var selectButton: Button

    private val backgroundColorValue = Color.rgb(18, 18, 24)
    private val green = Color.rgb(120, 220, 170)
    private val white = Color.WHITE
    private val muted = Color.LTGRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
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

        chat = TextView(this).apply {
            text = "Ari está esperando que cargues el modelo."
            textSize = 16f
            setTextColor(white)
            setPadding(8, 12, 8, 12)
        }

        val scroll = ScrollView(this).apply {
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
            isEnabled = false
            setOnClickListener { sendMessage() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(selectButton)
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
                val modelFile = File(filesDir, "gemma-model.litertlm")
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
                status.text = "Gemma cargado. Ari está listo."
                chat.append("\n\nAri: ¡Hola! Ya estoy listo para conversar.")
                sendButton.isEnabled = true
                selectButton.isEnabled = true
            }
        } catch (e: Exception) {
            runOnUiThread {
                status.text =
                    "No se pudo iniciar el modelo: ${e.message}"
                selectButton.isEnabled = true
                sendButton.isEnabled = false
            }
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()

        if (text.isEmpty() || !modelReady || busy) return

        val activeConversation = conversation ?: return

        input.text.clear()
        chat.append("\n\nTú: $text\n\nAri: ")
        status.text = "Ari está pensando..."
        busy = true
        sendButton.isEnabled = false

        activeConversation.sendMessageAsync(
            text,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val chunk = message.toString()
                    runOnUiThread {
                        chat.append(chunk)
                    }
                }

                override fun onDone() {
                    runOnUiThread {
                        busy = false
                        status.text = "Gemma listo."
                        sendButton.isEnabled = true
                    }
                }

                override fun onError(throwable: Throwable) {
                    runOnUiThread {
                        busy = false
                        status.text =
                            "Error de respuesta: ${throwable.message}"
                        chat.append("\n[Error al generar la respuesta]")
                        sendButton.isEnabled = true
                    }
                }
            }
        )
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
    }

    companion object {
        private const val PICK_MODEL = 101
    }
}
