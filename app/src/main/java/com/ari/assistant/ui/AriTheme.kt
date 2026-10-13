package com.ari.assistant.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue

/**
 * Colores y medidas de Ari: oscuro, como la interfaz de Claude, con el color
 * hielo como acento. Los mismos valores están en res/values/colors.xml para
 * los widgets y el icono.
 */
object AriTheme {
    val background = Color.parseColor("#16181C")
    val surface = Color.parseColor("#1F2227")
    val bubble = Color.parseColor("#2A2F36")
    val border = Color.parseColor("#2F353D")
    val text = Color.parseColor("#E8EEF2")
    val muted = Color.parseColor("#9AA6B2")
    // El color de Ari (hielo, si no eliges otro en Ajustes → Color de Ari).
    var iceLight = Color.parseColor("#DEF6FD")
        private set
    var ice = Color.parseColor("#9ED8EE")
        private set
    var iceDeep = Color.parseColor("#5FB8DA")
        private set
    /** Sube cada vez que cambia el color, para que la esfera rehaga sus degradados. */
    var colorVersion = 0
        private set

    fun setColors(light: Int, main: Int, deep: Int) {
        if (light == iceLight && main == ice && deep == iceDeep) return
        iceLight = light
        ice = main
        iceDeep = deep
        colorVersion++
    }
    val onIce = Color.parseColor("#0B1A22")
    val codeBackground = Color.parseColor("#262A31")
    val warning = Color.parseColor("#F2B8A0")

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    /** Fondo redondeado, con borde opcional. */
    fun rounded(context: Context, color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            strokeColor?.let { setStroke(dp(context, 1f), it) }
        }

    fun circle(color: Int, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            strokeColor?.let { setStroke(strokePx, it) }
        }
}
