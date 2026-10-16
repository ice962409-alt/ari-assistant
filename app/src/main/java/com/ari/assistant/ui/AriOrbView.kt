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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ari en persona: una esfera color hielo con cara que respira, parpadea y reacciona.
 *
 * - Hablando: la boca cambia de forma sílaba por sílaba (a, e, i, o, u, m), con lengua
 *   y dientes, y se abre más en cada palabra que dice ([talk]).
 * - Pensando: murmura con la boca de lado, mira hacia arriba y sube las cejas.
 * - Escuchando: brilla y hace ondas con tu voz ([level]).
 * - Todo cambia con suavidad: los ojos, la boca y la mirada van hacia su forma nueva.
 */
class AriOrbView(context: Context) : View(context) {

    enum class Mood { IDLE, LISTENING, THINKING, SPEAKING, HAPPY }

    var mood: Mood = Mood.IDLE
        set(value) {
            if (field == value) return
            field = value
            nextSyllable = 0L
            invalidate()
        }

    /** true cuando algo la tapa o es una carita fija (en el chat): no se anima, para no gastar batería. */
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

    /**
     * Ari dice algo: abre la boca y sigue "hablando" un ratito según cuánto texto es.
     * Se llama con cada palabra que dice en voz o cada pedazo de texto que escribe.
     */
    fun talk(chars: Int = 6) {
        val now = SystemClock.uptimeMillis()
        talkUntil = max(talkUntil, now + (chars * 45L).coerceIn(260L, 3_000L))
        energy = 1f
        if (now >= nextSyllable - 40) nextSyllable = 0L
        if (paused) return
        invalidate()
    }

    private var smoothLevel = 0f
    private val start = SystemClock.uptimeMillis()
    private var lastFrame = start
    private var talkUntil = 0L
    private var energy = 0f

    // Boca: lo que se ve y hacia dónde va (se mueve con suavidad).
    private var mouthOpen = 0f
    private var mouthWide = 1f
    private var mouthRound = 0f
    private var mouthShift = 0f
    private var targetOpen = 0f
    private var targetWide = 1f
    private var targetRound = 0f
    private var targetShift = 0f
    private var nextSyllable = 0L
    private var smile = 0.5f

    // Ojos: parpadeo suave, mirada y cejas.
    private var blinkStart = start + 2_500
    private var doubleBlink = false
    private var lookX = 0f
    private var lookY = 0f
    private var squint = 0f
    private var brow = 0f

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AriTheme.onIce }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AriTheme.onIce
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AriTheme.ice
        style = Paint.Style.STROKE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AriTheme.ice }
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
    private val tongue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 128, 150) }
    private val teeth = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255) }
    private val clip = Path()
    private val oval = RectF()
    private val path = Path()
    private val mouthPath = Path()
    private var shaderSize = 0f
    private var shaderColors = -1
    private val sides = floatArrayOf(-1f, 1f)

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Al volver a verse, retoma la animación.
        if (visibility == VISIBLE) invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shaderSize = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now
        val t = (now - start) / 1000f
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        val radius = size * 0.30f
        if (radius <= 0f) return
        // Las caritas chicas (en la barra o junto a un mensaje) llevan menos adornos.
        val small = size < AriTheme.dp(context, 64f)

        val talking = mood == Mood.SPEAKING || now < talkUntil
        val murmuring = mood == Mood.THINKING && !talking
        energy = max(0f, energy - dt * 2.2f)
        updateMouth(now, talking, murmuring)
        val ease = 1f - exp(-dt * 20f)
        mouthOpen += (targetOpen - mouthOpen) * ease
        mouthWide += (targetWide - mouthWide) * ease
        mouthRound += (targetRound - mouthRound) * ease
        mouthShift += (targetShift - mouthShift) * (1f - exp(-dt * 6f))

        smoothLevel += (level - smoothLevel) * 0.25f
        val breathe = sin(t * 2.0f) * 0.025f
        val bounce = when {
            talking -> mouthOpen * 0.03f
            mood == Mood.LISTENING -> smoothLevel * 0.06f
            mood == Mood.HAPPY -> abs(sin(t * 6f)) * 0.05f
            else -> 0f
        }
        val r = radius * (1f + breathe + bounce)

        // Halo: crece con tu voz o al hablar.
        val haloStrength = when {
            mood == Mood.LISTENING -> 0.35f + smoothLevel * 0.65f
            talking -> 0.42f + mouthOpen * 0.35f
            mood == Mood.THINKING -> 0.3f + (sin(t * 3f) + 1f) * 0.1f
            else -> 0.25f + (sin(t * 2f) + 1f) * 0.05f
        }
        if (shaderSize != size || shaderColors != AriTheme.colorVersion) buildShaders(cx, cy, size, radius)
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

        drawBody(canvas, cx, cy, radius, r, t, small)
        drawEyes(canvas, cx, cy, r, t, now, dt, talking)
        drawMouth(canvas, cx, cy, r, dt)

        // Destellos alrededor cuando está feliz.
        if (mood == Mood.HAPPY && !small) {
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
                canvas.drawCircle(cx + cos(angle) * d, cy + sin(angle) * d, size * (if (small) 0.03f else 0.018f), dot)
            }
        }

        if (isShown && !paused) {
            // Quieta, basta con 20 cuadros por segundo (gasta menos batería); hablando o pensando, todos.
            if (mood == Mood.IDLE && !talking && abs(mouthOpen) < 0.02f) postInvalidateDelayed(50) else postInvalidateOnAnimation()
        }
    }

    /** Elige la siguiente sílaba: forma de la boca a la que va. */
    private fun updateMouth(now: Long, talking: Boolean, murmuring: Boolean) {
        if (now < nextSyllable) return
        when {
            talking -> {
                // Cada sílaba: abierta (a), ancha (e, i), redonda (o, u) o cerrada (m, p, b).
                val v = VISEMES[Random.nextInt(VISEMES.size)]
                val loud = 0.75f + energy * 0.25f
                targetOpen = v[0] * loud
                targetWide = v[1]
                targetRound = v[2]
                targetShift = 0f
                nextSyllable = now + 85 + Random.nextLong(80)
            }
            murmuring -> {
                // "Mmm…": la boca de lado, se abre poquito y se cierra.
                val v = MURMURS[Random.nextInt(MURMURS.size)]
                targetOpen = v[0]
                targetWide = v[1]
                targetRound = v[2]
                if (Random.nextInt(4) == 0) targetShift = if (Random.nextBoolean()) 0.09f else -0.09f
                nextSyllable = now + 180 + Random.nextLong(200)
            }
            else -> {
                targetOpen = 0f
                targetWide = 1f
                targetRound = 0f
                targetShift = 0f
                nextSyllable = now + 120
            }
        }
    }

    private fun buildShaders(cx: Float, cy: Float, size: Float, radius: Float) {
        shaderSize = size
        shaderColors = AriTheme.colorVersion
        ring.color = AriTheme.ice
        dot.color = AriTheme.ice
        glow.shader = RadialGradient(
            cx, cy, size * 0.5f,
            intArrayOf(withAlpha(AriTheme.ice, 150), withAlpha(AriTheme.ice, 0)),
            floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP
        )
        // El degradado del cuerpo se calcula una vez; al respirar se escala el lienzo, no se rehace.
        body.shader = RadialGradient(
            cx - radius * 0.35f, cy - radius * 0.4f, radius * 1.6f,
            intArrayOf(AriTheme.iceLight, AriTheme.ice, AriTheme.iceDeep),
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

    private fun drawBody(canvas: Canvas, cx: Float, cy: Float, radius: Float, r: Float, t: Float, small: Boolean) {
        canvas.save()
        canvas.scale(r / radius, r / radius, cx, cy)
        canvas.drawCircle(cx, cy, radius, body)
        canvas.save()
        canvas.clipPath(clip)
        if (!small) {
            // Remolino de luz que gira despacio dentro de la esfera (más rápido cuando piensa).
            val spin = t * (if (mood == Mood.THINKING) 1.6f else 0.35f)
            for (i in 0 until 2) {
                canvas.save()
                canvas.rotate(Math.toDegrees(spin + i * PI).toFloat(), cx, cy)
                swirl.color = if (i == 0) Color.argb(34, 255, 255, 255) else withAlpha(AriTheme.iceDeep, 30)
                oval.set(cx - radius * 0.95f, cy - radius * 0.28f, cx + radius * 0.35f, cy + radius * 0.12f)
                canvas.drawOval(oval, swirl)
                canvas.restore()
            }
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
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, now: Long, dt: Float, talking: Boolean) {
        // Mirada: alrededor y hacia arriba al pensar; a ti al escucharte; un vaivén suave si no.
        val wantX = when (mood) {
            Mood.THINKING -> sin(t * 1.1f) * 0.13f
            Mood.LISTENING -> 0f
            else -> sin(t * 0.5f) * 0.03f
        }
        val wantY = if (mood == Mood.THINKING) -0.07f else 0f
        val follow = 1f - exp(-dt * 8f)
        lookX += (wantX - lookX) * follow
        lookY += (wantY - lookY) * follow
        // Al hablar entrecierra un poquito los ojos (sonríe con ellos) y sube las cejas al recalcar.
        val wantSquint = when {
            mood == Mood.HAPPY -> 1f
            talking -> 0.18f + energy * 0.12f
            else -> 0f
        }
        squint += (wantSquint - squint) * follow
        val wantBrow = when {
            mood == Mood.THINKING -> 1f
            talking -> energy * 0.6f
            mood == Mood.LISTENING -> 0.35f
            else -> 0f
        }
        brow += (wantBrow - brow) * follow
        // Sonrisa de base según el ánimo.
        val wantSmile = when (mood) {
            Mood.HAPPY -> 1f
            Mood.THINKING -> 0.15f
            Mood.LISTENING -> 0.45f
            else -> 0.55f
        }
        smile += (wantSmile - smile) * follow

        // Parpadeo suave (a veces doble), que baja y sube el párpado.
        if (now > blinkStart + BLINK_MS) {
            if (doubleBlink) {
                blinkStart = now + 120
                doubleBlink = false
            } else {
                blinkStart = now + 2_200 + Random.nextLong(2_800)
                doubleBlink = Random.nextInt(5) == 0
            }
        }
        val lid = if (now in blinkStart..(blinkStart + BLINK_MS)) {
            val p = (now - blinkStart).toFloat() / BLINK_MS
            1f - sin(p * PI.toFloat())
        } else 1f

        val eyeY = cy - r * 0.08f + lookY * r
        val eyeDx = r * 0.32f
        val eyeW = r * 0.14f
        val baseH = if (mood == Mood.LISTENING) r * 0.30f else r * 0.26f
        val eyeH = baseH * max(0.06f, lid * (1f - squint * 0.35f))
        line.strokeWidth = r * 0.075f
        for (side in sides) {
            val ex = cx + side * eyeDx + lookX * r
            when {
                mood == Mood.HAPPY && squint > 0.6f -> {
                    // Ojitos felices: arcos hacia arriba.
                    path.reset()
                    path.moveTo(ex - eyeW, eyeY + baseH * 0.15f)
                    path.quadTo(ex, eyeY - baseH * 0.55f, ex + eyeW, eyeY + baseH * 0.15f)
                    canvas.drawPath(path, line)
                }
                eyeH < eyeW * 0.7f -> canvas.drawLine(ex - eyeW * 0.8f, eyeY, ex + eyeW * 0.8f, eyeY, line)
                else -> {
                    oval.set(ex - eyeW / 2, eyeY - eyeH / 2, ex + eyeW / 2, eyeY + eyeH / 2)
                    canvas.drawOval(oval, face)
                    // Dos brillitos en el ojo: la hacen ver viva. Siguen un poco la mirada.
                    val gx = ex - eyeW * 0.12f + lookX * r * 0.15f
                    canvas.drawCircle(gx, eyeY - eyeH * 0.22f, eyeW * 0.2f, sparkle)
                    canvas.drawCircle(gx + eyeW * 0.22f, eyeY + eyeH * 0.12f, eyeW * 0.08f, sparkle)
                }
            }
            // Cejas: suben al pensar o al recalcar algo.
            if (brow > 0.05f) {
                line.alpha = (255 * brow.coerceAtMost(1f)).toInt()
                path.reset()
                val browY = eyeY - baseH * (0.8f + brow * 0.15f)
                path.moveTo(ex - eyeW * 0.9f, browY + side * r * 0.02f)
                path.quadTo(ex, browY - r * 0.06f * brow, ex + eyeW * 0.9f, browY - side * r * 0.02f)
                canvas.drawPath(path, line)
                line.alpha = 255
            }
            // Mejillas: apenas se notan, y se sonrojan cuando está feliz o platicando.
            cheek.alpha = when {
                mood == Mood.HAPPY -> 120
                talking -> 75
                else -> 35
            }
            oval.set(ex + side * eyeW * 0.4f - r * 0.11f, cy + r * 0.14f, ex + side * eyeW * 0.4f + r * 0.11f, cy + r * 0.24f)
            canvas.drawOval(oval, cheek)
        }
    }

    private fun drawMouth(canvas: Canvas, cx: Float, cy: Float, r: Float, dt: Float) {
        val mx = cx + mouthShift * r
        val my = cy + r * 0.32f
        if (mouthOpen < 0.08f) {
            // Boca cerrada: sonrisa (más o menos según el ánimo), o labios juntos si murmura.
            val wide = (0.13f + smile * 0.11f) * mouthWide
            line.strokeWidth = r * 0.07f
            path.reset()
            path.moveTo(mx - r * wide, my - r * 0.03f)
            path.quadTo(mx, my + r * (0.02f + smile * 0.17f), mx + r * wide, my - r * 0.03f)
            canvas.drawPath(path, line)
            return
        }
        // Boca abierta: la forma sale de la sílaba (ancha para "e", redonda para "o").
        val w = r * 0.19f * mouthWide * (1f - 0.35f * mouthRound) * (0.85f + smile * 0.2f)
        val h = r * 0.22f * mouthOpen
        val top = 0.25f + 0.75f * mouthRound
        val bottom = 1.25f + 0.25f * mouthRound
        // Las comisuras suben un poquito: habla sonriendo.
        val lift = r * 0.03f * smile * (1f - mouthRound)
        mouthPath.reset()
        if (mouthRound > 0.55f) {
            // "o" y "u": boca redonda.
            oval.set(mx - w, my - h * 0.75f, mx + w, my + h * 0.95f)
            mouthPath.addOval(oval, Path.Direction.CW)
        } else {
            mouthPath.moveTo(mx - w, my - lift)
            mouthPath.cubicTo(mx - w * 0.6f, my - h * top, mx + w * 0.6f, my - h * top, mx + w, my - lift)
            mouthPath.cubicTo(mx + w * 0.6f, my + h * bottom, mx - w * 0.6f, my + h * bottom, mx - w, my - lift)
            mouthPath.close()
        }
        canvas.drawPath(mouthPath, face)
        canvas.save()
        canvas.clipPath(mouthPath)
        // Dientes de arriba cuando la boca está bien abierta y no redonda.
        if (mouthOpen > 0.45f && mouthRound < 0.5f) {
            oval.set(mx - w * 0.55f, my - h * top - h * 0.2f, mx + w * 0.55f, my - h * top * 0.35f + h * 0.12f)
            canvas.drawRoundRect(oval, h * 0.15f, h * 0.15f, teeth)
        }
        // Lengüita abajo.
        if (mouthOpen > 0.3f) canvas.drawCircle(mx, my + h * (if (mouthRound > 0.55f) 0.9f else bottom * 0.95f), w * 0.55f, tongue)
        canvas.restore()
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

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val BLINK_MS = 170L

        /** Formas de la boca al hablar: abierta, ancho y redondez. */
        val VISEMES = arrayOf(
            floatArrayOf(0.95f, 1.0f, 0f), // a
            floatArrayOf(0.85f, 1.05f, 0f), // a
            floatArrayOf(0.55f, 1.25f, 0f), // e
            floatArrayOf(0.35f, 1.3f, 0f), // i
            floatArrayOf(0.7f, 0.8f, 0.85f), // o
            floatArrayOf(0.4f, 0.65f, 1f), // u
            floatArrayOf(0.04f, 0.95f, 0f), // m, p, b
            floatArrayOf(0.6f, 1.1f, 0.2f), // s, d, t
        )

        /** Al pensar: "mmm", "hmm", una "u" chiquita. */
        val MURMURS = arrayOf(
            floatArrayOf(0.04f, 0.75f, 0f),
            floatArrayOf(0.04f, 0.6f, 0.4f),
            floatArrayOf(0.2f, 0.6f, 1f),
            floatArrayOf(0.15f, 0.85f, 0.3f),
        )
    }
}
