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
    fun showWelcome() {
        if (welcome != null || column.childCount > 0) return
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(96f), 0, dp(32f))
        }
        box.addView(View(context).apply {
            background = AriTheme.circle(AriTheme.background, AriTheme.ice, dp(3f))
        }, LinearLayout.LayoutParams(dp(56f), dp(56f)).apply { bottomMargin = dp(20f) })
        box.addView(TextView(context).apply {
            text = "Hola, soy Ari"
            textSize = 28f
            setTypeface(Typeface.SERIF, Typeface.NORMAL)
            setTextColor(AriTheme.text)
            gravity = Gravity.CENTER
        })
        box.addView(TextView(context).apply {
            text = "Tu asistente personal con IA local.\n¿En qué trabajamos hoy?"
            textSize = 16f
            setTextColor(AriTheme.muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
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
        column.removeAllViews()
    }

    fun addUser(text: String) {
        clearWelcome()
        finishAri()
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
        column.addView(row, spaced(top = 14f))
        scrollToEnd()
    }

    /** Empieza un mensaje de Ari; [who] es el agente que responde, si no es ella. */
    fun startAri(who: String? = null) {
        clearWelcome()
        finishAri()

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
        column.addView(label, spaced(top = 18f))

        val body = TextView(context).apply {
            textSize = 16f
            setTextColor(AriTheme.text)
            setLineSpacing(0f, 1.25f)
            setTextIsSelectable(true)
        }
        column.addView(body, spaced(top = 6f))
        current = body
        currentText.setLength(0)
        startThinking(body)
        scrollToEnd()
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
