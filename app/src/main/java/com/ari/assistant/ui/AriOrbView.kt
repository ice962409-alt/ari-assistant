package com.ari.assistant.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ari en persona: una esfera color hielo con ojos y boca que respira,
 * parpadea y reacciona. Mientras te escucha, brilla con tu voz; mientras
 * piensa, mira de un lado a otro; mientras habla, mueve la boca.
 * Es una caricatura provisional hasta tener el diseño definitivo.
 */
class AriOrbView(context: Context) : View(context) {

    enum class Mood { IDLE, LISTENING, THINKING, SPEAKING, HAPPY }

    var mood: Mood = Mood.IDLE
        set(value) {
            field = value
            invalidate()
        }

    /** true cuando algo la tapa (el modo live encima del chat): deja de animarse para no gastar batería. */
    var paused: Boolean = false
        set(value) {
            field = value
            if (!value) invalidate()
        }

    /** Volumen de tu voz (0 a 1) mientras Ari escucha. */
    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private var smoothLevel = 0f
    private var nextBlink = SystemClock.uptimeMillis() + 2500
    private val start = SystemClock.uptimeMillis()

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AriTheme.onIce }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AriTheme.onIce
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AriTheme.ice
        style = Paint.Style.STROKE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AriTheme.ice }
    // Detalles de la esfera: remolino de luz por dentro, sombra en el borde, brillo de orilla,
    // un tinte según el ánimo, mejillas, brillo en los ojos y destellos cuando está feliz.
    private val swirl = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cheek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 150, 170) }
    private val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val clip = Path()
    private val oval = RectF()
    private val path = Path()
    private var shaderSize = 0f
    private val sides = floatArrayOf(-1f, 1f)

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Al volver a verse, retoma la animación.
        if (visibility == VISIBLE) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val t = (now - start) / 1000f
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        val radius = size * 0.30f
        if (radius <= 0f) return

        smoothLevel += (level - smoothLevel) * 0.25f
        val breathe = sin(t * 2.0f) * 0.025f
        val bounce = when (mood) {
            Mood.SPEAKING -> abs(sin(t * 9f)) * 0.03f
            Mood.LISTENING -> smoothLevel * 0.06f
            Mood.HAPPY -> abs(sin(t * 6f)) * 0.05f
            else -> 0f
        }
        val r = radius * (1f + breathe + bounce)

        // Halo: crece con tu voz o al hablar.
        val haloStrength = when (mood) {
            Mood.LISTENING -> 0.35f + smoothLevel * 0.65f
            Mood.SPEAKING -> 0.45f + abs(sin(t * 5f)) * 0.3f
            Mood.THINKING -> 0.3f + (sin(t * 3f) + 1f) * 0.1f
            else -> 0.25f + (sin(t * 2f) + 1f) * 0.05f
        }
        if (shaderSize != size) {
            shaderSize = size
            glow.shader = RadialGradient(
                cx, cy, size * 0.5f,
                intArrayOf(Color.argb(150, 158, 216, 238), Color.argb(0, 158, 216, 238)),
                floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP
            )
            // El degradado del cuerpo se calcula una vez; al respirar se escala el lienzo, no se rehace.
            body.shader = RadialGradient(
                cx - radius * 0.35f, cy - radius * 0.4f, radius * 1.6f,
                intArrayOf(Color.rgb(222, 246, 253), AriTheme.ice, AriTheme.iceDeep),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            // Sombra suave abajo a la derecha: le da volumen, como una canica.
            shade.shader = RadialGradient(
                cx - radius * 0.2f, cy - radius * 0.25f, radius * 1.25f,
                intArrayOf(Color.argb(0, 11, 26, 34), Color.argb(0, 11, 26, 34), Color.argb(80, 11, 26, 34)),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
            clip.reset()
            clip.addCircle(cx, cy, radius, Path.Direction.CW)
        }
        glow.alpha = (255 * haloStrength).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, size * 0.5f, glow)

        // Ondas mientras escucha.
        if (mood == Mood.LISTENING) {
            for (i in 0 until 2) {
                val phase = ((t * 0.9f + i * 0.5f) % 1f)
                ring.strokeWidth = size * 0.01f
                ring.alpha = ((1f - phase) * 120 * (0.4f + smoothLevel)).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, r * (1.05f + phase * 0.45f), ring)
            }
        }

        // Cuerpo con luz arriba a la izquierda.
        canvas.save()
        canvas.scale(r / radius, r / radius, cx, cy)
        canvas.drawCircle(cx, cy, radius, body)
        canvas.save()
        canvas.clipPath(clip)
        // Remolino de luz que gira despacio dentro de la esfera (más rápido cuando piensa).
        val spin = t * (if (mood == Mood.THINKING) 1.6f else 0.35f)
        for (i in 0 until 2) {
            canvas.save()
            canvas.rotate(Math.toDegrees((spin + i * PI).toDouble()).toFloat(), cx, cy)
            swirl.color = if (i == 0) Color.argb(34, 255, 255, 255) else Color.argb(30, 95, 184, 218)
            oval.set(cx - radius * 0.95f, cy - radius * 0.28f, cx + radius * 0.35f, cy + radius * 0.12f)
            canvas.drawOval(oval, swirl)
            canvas.restore()
        }
        // Tinte según el ánimo: más cian al escucharte, lila al pensar, tibio al estar feliz.
        val tintColor = when (mood) {
            Mood.LISTENING -> Color.argb((30 + smoothLevel * 70).toInt(), 127, 227, 255)
            Mood.THINKING -> Color.argb(42, 185, 168, 255)
            Mood.HAPPY -> Color.argb(36, 255, 205, 170)
            Mood.SPEAKING -> Color.argb(24, 200, 240, 255)
            Mood.IDLE -> Color.TRANSPARENT
        }
        if (tintColor != Color.TRANSPARENT) {
            tint.color = tintColor
            canvas.drawCircle(cx, cy, radius, tint)
        }
        canvas.drawCircle(cx, cy, radius, shade)
        canvas.restore()
        oval.set(cx - radius * 0.55f, cy - radius * 0.75f, cx - radius * 0.05f, cy - radius * 0.45f)
        canvas.drawOval(oval, shine)
        // Brillo de orilla arriba a la izquierda.
        rim.strokeWidth = radius * 0.035f
        rim.alpha = 110
        oval.set(cx - radius * 0.93f, cy - radius * 0.93f, cx + radius * 0.93f, cy + radius * 0.93f)
        canvas.drawArc(oval, 195f, 70f, false, rim)
        canvas.restore()

        // Ojos: miran alrededor al pensar y parpadean de vez en cuando.
        val look = when (mood) {
            Mood.THINKING -> sin(t * 1.6f) * r * 0.12f
            Mood.LISTENING -> 0f
            else -> sin(t * 0.5f) * r * 0.03f
        }
        val lookUp = if (mood == Mood.THINKING) -r * 0.06f else 0f
        if (now > nextBlink + 140) nextBlink = now + 2200 + Random.nextLong(2600)
        val blinking = now in nextBlink..(nextBlink + 140)
        val eyeY = cy - r * 0.08f + lookUp
        val eyeDx = r * 0.32f
        val eyeW = r * 0.14f
        val eyeH = if (mood == Mood.LISTENING) r * 0.30f else r * 0.26f
        line.strokeWidth = r * 0.075f
        for (side in sides) {
            val ex = cx + side * eyeDx + look
            when {
                mood == Mood.HAPPY -> {
                    path.reset()
                    path.moveTo(ex - eyeW, eyeY + eyeH * 0.15f)
                    path.quadTo(ex, eyeY - eyeH * 0.55f, ex + eyeW, eyeY + eyeH * 0.15f)
                    canvas.drawPath(path, line)
                }
                blinking -> canvas.drawLine(ex - eyeW, eyeY, ex + eyeW, eyeY, line)
                else -> {
                    oval.set(ex - eyeW / 2, eyeY - eyeH / 2, ex + eyeW / 2, eyeY + eyeH / 2)
                    canvas.drawOval(oval, face)
                    // Brillito en el ojo: la hace ver viva.
                    canvas.drawCircle(ex - eyeW * 0.12f, eyeY - eyeH * 0.22f, eyeW * 0.2f, sparkle)
                }
            }
            // Cejas que se levantan un poco cuando piensa.
            if (mood == Mood.THINKING) {
                path.reset()
                val browY = eyeY - eyeH * 0.85f
                path.moveTo(ex - eyeW * 0.9f, browY + side * r * 0.02f)
                path.quadTo(ex, browY - r * 0.06f, ex + eyeW * 0.9f, browY - side * r * 0.02f)
                canvas.drawPath(path, line)
            }
            // Mejillas: apenas se notan, y se sonrojan cuando está feliz o platicando.
            cheek.alpha = when (mood) {
                Mood.HAPPY -> 120
                Mood.SPEAKING -> 70
                else -> 35
            }
            oval.set(ex + side * eyeW * 0.4f - r * 0.11f, cy + r * 0.14f, ex + side * eyeW * 0.4f + r * 0.11f, cy + r * 0.24f)
            canvas.drawOval(oval, cheek)
        }

        // Boca: sonrisa, o se abre y cierra mientras habla.
        val mouthY = cy + r * 0.32f
        if (mood == Mood.SPEAKING) {
            val open = (0.25f + abs(sin(t * 11f)) * 0.75f) * r * 0.16f
            oval.set(cx - r * 0.14f, mouthY - open / 2, cx + r * 0.14f, mouthY + open / 2)
            canvas.drawOval(oval, face)
        } else {
            val wide = if (mood == Mood.HAPPY) 0.24f else 0.17f
            path.reset()
            path.moveTo(cx - r * wide, mouthY - r * 0.03f)
            path.quadTo(cx, mouthY + r * (if (mood == Mood.HAPPY) 0.2f else 0.12f), cx + r * wide, mouthY - r * 0.03f)
            canvas.drawPath(path, line)
        }

        // Destellos alrededor cuando está feliz.
        if (mood == Mood.HAPPY) {
            for (i in 0 until 3) {
                val angle = (-PI / 2 + (i - 1) * 0.9).toFloat()
                val twinkle = (sin(t * 6f + i * 2f) + 1f) / 2f
                val d = r * 1.3f
                drawStar(canvas, cx + cos(angle) * d, cy + sin(angle) * d, size * (0.02f + 0.02f * twinkle))
            }
        }

        // Tres puntitos que giran mientras piensa.
        if (mood == Mood.THINKING) {
            for (i in 0 until 3) {
                val angle = t * 2.4f + i * (2 * PI / 3).toFloat()
                val d = r * 1.28f
                canvas.drawCircle(cx + cos(angle) * d, cy + sin(angle) * d, size * 0.018f, dot)
            }
        }

        if (isShown && !paused) postInvalidateOnAnimation()
    }

    /** Estrellita de cuatro puntas. */
    private fun drawStar(canvas: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s)
        path.quadTo(x, y, x + s, y)
        path.quadTo(x, y, x, y + s)
        path.quadTo(x, y, x - s, y)
        path.quadTo(x, y, x, y - s)
        path.close()
        canvas.drawPath(path, sparkle)
    }
}
