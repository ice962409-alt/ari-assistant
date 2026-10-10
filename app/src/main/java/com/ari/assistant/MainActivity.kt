package com.ari.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(18, 18, 24))
        }

        val title = TextView(this).apply {
            text = "ARI"
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Tu asistente personal"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "\nSistema inicializado\nMemoria local preparada\nModelo de IA pendiente de conexión"
            textSize = 15f
            setTextColor(Color.rgb(120, 220, 170))
            gravity = Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(status)

        setContentView(layout)
    }
}
