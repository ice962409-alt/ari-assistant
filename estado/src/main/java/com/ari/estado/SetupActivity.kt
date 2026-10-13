package com.ari.estado

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Configuración de Ari Estado: explica cómo crear la llave de solo lectura y
 * la guarda cifrada. La llave se pega aquí, nunca en un chat.
 */
class SetupActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var tokenInput: EditText
    private lateinit var tokenInfo: TextView

    private val bg = Color.rgb(22, 24, 28)
    private val green = Color.rgb(158, 216, 238)
    private val muted = Color.rgb(154, 166, 178)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(bg)
        }

        column.addView(text("Ari Estado", 28f, Color.WHITE))
        column.addView(
            text(
                "Este widget muestra en qué trabaja Claude, su barra de progreso y te avisa cuando te necesita. " +
                    "Lee el estado del repositorio privado Ari-estado con una llave de solo lectura.",
                15f, muted
            )
        )

        column.addView(text("Cómo crear la llave (una sola vez)", 18f, green))
        column.addView(
            text(
                "1. Toca \"Abrir GitHub\" (inicia sesión si te lo pide).\n" +
                    "2. Token name: Ari Estado widget.\n" +
                    "3. Expiration: 90 días (cuando venza, crea otra).\n" +
                    "4. Repository access: Only select repositories → elige Ari-estado.\n" +
                    "5. Permissions → Repository permissions → Contents: Read-only.\n" +
                    "6. Toca Generate token, copia la llave (empieza con github_pat_) y pégala abajo.\n\n" +
                    "No la mandes por chat ni la guardes en notas: solo va aquí.",
                15f, Color.WHITE
            )
        )
        column.addView(button("Abrir GitHub") {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))
            )
        })

        tokenInput = EditText(this).apply {
            hint = if (TokenVault.hasToken(this@SetupActivity)) "Llave guardada (pega otra para cambiarla)" else "Pega aquí la llave"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 35, 44))
            setPadding(24, 24, 24, 24)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        column.addView(tokenInput, spaced())
        tokenInfo = text("", 13f, muted)
        column.addView(tokenInfo)
        showTokenInfo()
        column.addView(button("Guardar y probar") { saveAndTest() })
        column.addView(button("Borrar la llave") {
            TokenVault.clear(this)
            RefreshJob.cancelAll(this)
            StatusWidget.renderAll(this)
            status.text = "Llave borrada. El widget dejó de consultar."
            showTokenInfo()
        })

        status = text("", 15f, green)
        column.addView(status, spaced())

        // Si la app se cerró sola, se muestra el motivo para poder corregirlo.
        CrashLog.last(this)?.let { trace ->
            column.addView(text("La app se cerró inesperadamente. Toma una captura de esto y mándasela a Claude:", 15f, Color.rgb(255, 155, 106)))
            column.addView(text(trace.lines().take(12).joinToString("\n"), 12f, muted))
            column.addView(button("Borrar este registro") {
                CrashLog.clear(this)
                status.text = "Registro borrado."
            })
        }
        StatusStore.error(this)?.let { status.text = "Último aviso: $it" }

        column.addView(text("Para agregar el widget", 18f, green))
        column.addView(
            text(
                "Mantén presionado un espacio vacío de la pantalla de inicio → Widgets → Ari Estado → " +
                    "\"Claude trabajando\" → arrástralo.",
                15f, Color.WHITE
            )
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(column)
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun saveAndTest() {
        // Una llave de GitHub no lleva espacios ni saltos de línea: se quitan por si se colaron al copiar.
        val token = tokenInput.text.toString().replace(Regex("\\s"), "")
        if (token.isEmpty()) {
            status.text = if (TokenVault.hasToken(this)) "Probando la llave guardada…" else "Primero pega la llave."
            if (!TokenVault.hasToken(this)) return
        } else {
            TokenVault.save(this, token)
            tokenInput.text.clear()
            tokenInput.hint = "Llave guardada (pega otra para cambiarla)"
            status.text = "Llave guardada. Probando…"
            showTokenInfo()
        }

        Thread {
            val saved = TokenVault.load(this)
            val message = try {
                if (saved == null) throw IllegalStateException("No se pudo leer la llave guardada.")
                val json = StatusFetcher.fetch(saved)
                StatusStore.saveStatus(this, json)
                val current = ClaudeStatus.parse(json)
                "¡Funciona! Claude: ${current.task.ifBlank { "sin tarea" }} (${current.progress}%)."
            } catch (e: Exception) {
                StatusStore.saveError(this, e.message ?: "No se pudo leer el estado.")
                "No funcionó: ${e.message}"
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                status.text = message
                RefreshJob.schedule(this)
                StatusWidget.renderAll(this)
            }
        }.start()
    }

    /**
     * Muestra solo el inicio de la llave guardada y cuántos caracteres tiene,
     * para comprobar que se pegó completa sin dejarla a la vista.
     */
    private fun showTokenInfo() {
        val token = TokenVault.load(this)
        tokenInfo.text = when {
            token == null -> ""
            !token.startsWith("github_pat_") && !token.startsWith("ghp_") ->
                "La llave guardada empieza con \"${token.take(4)}…\" (${token.length} caracteres). " +
                    "Una llave de GitHub empieza con github_pat_: quizá se copió otra cosa."
            token.length < 40 ->
                "Llave guardada: ${token.take(15)}… (${token.length} caracteres). Parece incompleta: " +
                    "una llave de GitHub tiene unos 93. Cópiala con el botón de copiar."
            else -> "Llave guardada: ${token.take(15)}… (${token.length} caracteres)."
        }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 16, 0, 16)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun spaced() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = 16 }
}
