package com.ari.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * La conversación al estilo de Claude: tus mensajes en burbujas a la derecha
 * y los de Ari sin burbuja, con su nombre arriba y el texto con formato.
 * Mientras Ari piensa se ven tres puntos animados; la respuesta aparece a
 * medida que se escribe.
 */
class ChatView(context: Context) : ScrollView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16f), dp(8f), dp(16f), dp(16f))
    }

    private var welcome: View? = null

    /** Toque en una sugerencia de la bienvenida (lo envía como si lo escribieras). */
    var onSuggestion: ((String) -> Unit)? = null
    private var welcomeOrb: AriOrbView? = null

    /** Pausa la carita de la bienvenida mientras algo la tapa (el modo live). */
    fun pauseWelcome(paused: Boolean) {
        welcomeOrb?.paused = paused
    }
    private var olderButton: View? = null
    private var current: TextView? = null
    private val currentText = StringBuilder()
    private var thinking: ValueAnimator? = null

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Pantalla de bienvenida cuando todavía no hay mensajes. */
    fun showWelcome(userName: String = "") {
        if (welcome != null || column.childCount > 0) return
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(96f), 0, dp(32f))
        }
        box.addView(AriOrbView(context).also { welcomeOrb = it }, LinearLayout.LayoutParams(dp(120f), dp(120f)).apply { bottomMargin = dp(12f) })
        box.addView(TextView(context).apply {
            text = if (userName.isBlank()) "Hola, soy Ari" else "Hola, $userName"
            textSize = 28f
            setTypeface(Typeface.SERIF, Typeface.NORMAL)
            setTextColor(AriTheme.text)
            gravity = Gravity.CENTER
        })
        box.addView(TextView(context).apply {
            text = (if (userName.isBlank()) "Tu asistente personal" else "Soy Ari, tu asistente personal") +
                " con IA local.\n¿En qué trabajamos hoy?"
            textSize = 16f
            setTextColor(AriTheme.muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
        })
        // "¿Qué puedes hacer?": un toque y Ari cuenta todo lo que sabe hacer.
        box.addView(TextView(context).apply {
            text = "¿Qué puedes hacer?"
            textSize = 15f
            setTextColor(AriTheme.text)
            gravity = Gravity.CENTER
            background = AriTheme.rounded(context, AriTheme.bubble, 20f)
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            isClickable = true
            setOnClickListener { onSuggestion?.invoke(text.toString()) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(20f)
        })
        welcome = box
        column.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    /** Vacía la conversación, por ejemplo al recuperar un respaldo. */
    fun clear() {
        stopThinking()
        current = null
        currentText.setLength(0)
        welcome = null
        welcomeOrb = null
        olderButton = null
        column.removeAllViews()
    }

    fun addUser(text: String, image: java.io.File? = null) {
        clearWelcome()
        finishAri()
        image?.let { file -> thumbnail(file)?.let { column.addView(it, spaced(top = 14f)) } }
        column.addView(userRow(text), spaced(top = if (image != null) 6f else 14f))
        scrollToEnd()
    }

    /** Miniatura de la imagen que adjuntaste, a la derecha como tus mensajes. */
    private fun thumbnail(file: java.io.File): View? {
        val bitmap = android.graphics.BitmapFactory.decodeFile(
            file.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
        ) ?: return null
        val image = android.widget.ImageView(context).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_END
            maxWidth = dp(200f)
            maxHeight = dp(200f)
            clipToOutline = true
            background = AriTheme.rounded(context, AriTheme.bubble, 16f)
            contentDescription = "Imagen adjunta"
        }
        val row = FrameLayout(context)
        row.addView(image, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END
        ))
        return row
    }

    private fun userRow(text: String): View {
        val bubble = TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(AriTheme.text)
            setLineSpacing(0f, 1.15f)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = AriTheme.rounded(context, AriTheme.bubble, 20f)
            setTextIsSelectable(true)
            maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        }
        val row = FrameLayout(context)
        row.addView(bubble, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END
        ))
        return row
    }

    /** Empieza un mensaje de Ari; [who] es el agente que responde, si no es ella. */
    fun startAri(who: String? = null) {
        clearWelcome()
        finishAri()

        column.addView(ariLabel(who), spaced(top = 18f))
        val body = ariBody()
        column.addView(body, spaced(top = 6f))
        current = body
        currentText.setLength(0)
        startThinking(body)
        scrollToEnd()
    }

    private fun ariLabel(who: String?): View {
        val label = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        label.addView(View(context).apply {
            background = AriTheme.circle(AriTheme.ice)
        }, LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { rightMargin = dp(8f) })
        label.addView(TextView(context).apply {
            text = if (who == null) "Ari" else "Ari · $who"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(AriTheme.ice)
        })
        return label
    }

    private fun ariBody() = TextView(context).apply {
        textSize = 16f
        setTextColor(AriTheme.text)
        setLineSpacing(0f, 1.25f)
        setTextIsSelectable(true)
    }

    /**
     * Botón "Ver mensajes anteriores" arriba de la conversación. Así Ari abre
     * rápido aunque haya miles de mensajes: solo dibuja los últimos.
     */
    fun showOlderButton(onClick: () -> Unit) {
        if (olderButton != null) return
        val button = TextView(context).apply {
            text = "Ver mensajes anteriores"
            textSize = 14f
            setTextColor(AriTheme.ice)
            gravity = Gravity.CENTER
            setPadding(0, dp(12f), 0, dp(12f))
            setOnClickListener { onClick() }
        }
        olderButton = button
        column.addView(button, 0, spaced(top = 0f))
    }

    fun hideOlderButton() {
        olderButton?.let { column.removeView(it) }
        olderButton = null
    }

    /** Agrega arriba mensajes más antiguos sin mover lo que estás leyendo. */
    fun prependHistory(messages: List<Pair<Boolean, String>>) {
        if (messages.isEmpty()) return
        val heightBefore = column.height
        var index = if (olderButton != null) 1 else 0
        messages.forEach { (fromUser, text) ->
            if (fromUser) {
                column.addView(userRow(text), index++, spaced(top = 14f))
            } else {
                column.addView(ariLabel(null), index++, spaced(top = 18f))
                column.addView(ariBody().apply { this.text = Markdown.render(text) }, index++, spaced(top = 6f))
            }
        }
        // Cuando Android termine de medir los mensajes nuevos, se baja lo mismo que
        // crecieron: así sigues viendo lo que estabas leyendo.
        column.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                column.removeOnLayoutChangeListener(this)
                scrollBy(0, column.height - heightBefore)
            }
        })
    }

    /** Agrega texto al mensaje de Ari en curso (la respuesta que se va escribiendo). */
    fun appendAri(chunk: String) {
        val body = current ?: run { startAri(); current!! }
        stopThinking()
        currentText.append(chunk)
        body.text = Markdown.render(currentText.toString())
        scrollToEnd()
    }

    fun addAri(text: String, who: String? = null) {
        startAri(who)
        appendAri(text)
        finishAri()
    }

    /** Una nota pequeña de Ari, por ejemplo "El equipo no respondió". */
    fun addNote(text: String) {
        clearWelcome()
        finishAri()
        column.addView(TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(AriTheme.muted)
            setTypeface(typeface, Typeface.ITALIC)
        }, spaced(top = 12f))
        scrollToEnd()
    }

    /** Cierra el mensaje de Ari en curso. Si quedó vacío, lo quita. */
    fun finishAri() {
        stopThinking()
        val body = current ?: return
        if (currentText.isBlank()) {
            val index = column.indexOfChild(body)
            if (index > 0) column.removeViewAt(index - 1) // la etiqueta "Ari"
            column.removeView(body)
        }
        current = null
        currentText.setLength(0)
    }

    private fun startThinking(body: TextView) {
        stopThinking()
        body.setTextColor(AriTheme.muted)
        thinking = ValueAnimator.ofInt(0, 3).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { body.text = "Pensando" + ".".repeat((it.animatedValue as Int) + 1) }
            start()
        }
    }

    private fun stopThinking() {
        thinking?.cancel()
        thinking = null
        current?.setTextColor(AriTheme.text)
    }

    private fun clearWelcome() {
        welcome?.let { column.removeView(it) }
        welcome = null
        welcomeOrb = null
    }

    private fun scrollToEnd() {
        // scrollTo y no fullScroll: fullScroll mueve el foco y cerraría el teclado.
        post { smoothScrollTo(0, column.bottom) }
    }

    private fun spaced(top: Float) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun dp(value: Float) = AriTheme.dp(context, value)

    override fun onDetachedFromWindow() {
        stopThinking()
        super.onDetachedFromWindow()
    }
}
