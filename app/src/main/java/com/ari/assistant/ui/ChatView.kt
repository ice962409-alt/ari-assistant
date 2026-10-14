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

    private val SUGGESTIONS = listOf("¿Qué puedes hacer?", "Enséñame a usarte", "Ayúdame a organizar mi día")

    /** Toque en una sugerencia de la bienvenida (lo envía como si lo escribieras). */
    var onSuggestion: ((String) -> Unit)? = null
    private var welcomeOrb: AriOrbView? = null

    /** Pausa las caritas que se mueven mientras algo las tapa (el modo live). */
    fun pauseWelcome(paused: Boolean) {
        welcomeOrb?.paused = paused
        currentFace?.paused = paused || current == null
    }

    /**
     * Lo que hace Ari en el chat, para que su carita de arriba la acompañe:
     * true = está pensando; un número = acaba de escribir esa cantidad de letras.
     */
    var onAriThinking: (() -> Unit)? = null
    var onAriWrote: ((Int) -> Unit)? = null

    /** La carita junto al mensaje que Ari está escribiendo: piensa y mueve la boca. */
    private var currentFace: AriOrbView? = null
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
        // Sugerencias: un toque y se le dice a Ari (lo que puede hacer, aprender a usarla, algo del día).
        SUGGESTIONS.forEachIndexed { index, suggestion ->
            box.addView(TextView(context).apply {
                text = suggestion
                textSize = 15f
                setTextColor(AriTheme.text)
                gravity = Gravity.CENTER
                background = AriTheme.rounded(context, AriTheme.bubble, 20f)
                setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
                isClickable = true
                setOnClickListener { onSuggestion?.invoke(suggestion) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(if (index == 0) 20f else 8f)
            })
        }
        welcome = box
        // Al verte, Ari se pone contenta y te "dice" su saludo.
        welcomeOrb?.let { orb ->
            orb.mood = AriOrbView.Mood.HAPPY
            orb.postDelayed({
                orb.mood = AriOrbView.Mood.IDLE
                orb.talk(40)
            }, 1_400)
        }
        column.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    /** Vacía la conversación, por ejemplo al recuperar un respaldo. */
    fun clear() {
        stopThinking()
        current = null
        currentText.setLength(0)
        welcome = null
        welcomeOrb = null
        currentFace = null
        olderButton = null
        column.removeAllViews()
    }

    fun addUser(text: String, image: java.io.File? = null) {
        clearWelcome()
        finishAri()
        image?.let { file -> thumbnail(file)?.let { column.addView(it, spaced(top = 14f)) } }
        val row = userRow(text)
        column.addView(row, spaced(top = if (image != null) 6f else 14f))
        appear(row)
        // Mandaste algo: el chat vuelve abajo para seguir la respuesta.
        jumpToEnd()
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

        val label = ariLabel(who, live = true)
        column.addView(label, spaced(top = 18f))
        appear(label)
        val body = ariBody()
        column.addView(body, spaced(top = 6f))
        current = body
        currentText.setLength(0)
        startThinking(body)
        currentFace?.mood = AriOrbView.Mood.THINKING
        onAriThinking?.invoke()
        scrollToEnd()
    }

    /** "Ari" con su carita. La del mensaje en curso se mueve; las de antes se quedan quietas. */
    private fun ariLabel(who: String?, live: Boolean = false): View {
        val label = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val face = AriOrbView(context).apply { paused = !live }
        if (live) {
            currentFace?.let { rest(it) }
            currentFace = face
        }
        label.addView(face, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply {
            rightMargin = dp(4f)
            leftMargin = -dp(6f)
        })
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
        currentFace?.let {
            it.mood = AriOrbView.Mood.IDLE
            it.talk(chunk.length)
        }
        onAriWrote?.invoke(chunk.length)
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
        currentFace?.let { face ->
            if (face.mood == AriOrbView.Mood.THINKING) face.mood = AriOrbView.Mood.IDLE
            // Termina de "decir" lo último y se queda quieta (sin gastar batería).
            face.postDelayed({ if (face !== currentFace || current == null) rest(face) }, 3_200)
        }
        val body = current ?: return
        if (currentText.isBlank()) {
            val index = column.indexOfChild(body)
            if (index > 0) column.removeViewAt(index - 1) // la etiqueta "Ari"
            column.removeView(body)
        }
        current = null
        currentText.setLength(0)
    }

    private fun rest(face: AriOrbView) {
        face.mood = AriOrbView.Mood.IDLE
        face.paused = true
        face.invalidate()
    }

    /** Los mensajes nuevos aparecen suavecito, subiendo un poco. */
    private fun appear(view: View) {
        view.alpha = 0f
        view.translationY = dp(8f).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(220).start()
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

    // true mientras estás hasta abajo: el chat sigue a la respuesta de Ari. Si subes a leer,
    // se queda quieto donde estás hasta que bajes o toques "Ir al final".
    private var follow = true
    // Solo un desplazamiento hecho con el dedo puede dejar de seguir la respuesta.
    private var touched = false

    /** Avisa cuando el chat deja de seguir la respuesta (para mostrar "↓ Ir al final"). */
    var onFollowChanged: ((Boolean) -> Unit)? = null

    private fun scrollToEnd() {
        if (!follow) return
        // scrollTo y no fullScroll: fullScroll mueve el foco y cerraría el teclado.
        post { if (follow) smoothScrollTo(0, column.bottom) }
    }

    /** Vuelve hasta abajo y sigue la respuesta. */
    fun jumpToEnd() {
        setFollow(true)
        post { smoothScrollTo(0, column.bottom) }
    }

    private fun setFollow(value: Boolean) {
        if (follow == value) return
        follow = value
        onFollowChanged?.invoke(value)
    }

    private fun atBottom(): Boolean = scrollY + height >= column.bottom - dp(48f)

    override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN || ev.actionMasked == android.view.MotionEvent.ACTION_MOVE) touched = true
        return super.onTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) touched = true
        return super.onInterceptTouchEvent(ev)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!touched) return
        val bottom = atBottom()
        if (t < oldt && !bottom) setFollow(false) else if (bottom) setFollow(true)
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
