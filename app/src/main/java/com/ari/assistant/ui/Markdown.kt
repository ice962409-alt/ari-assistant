package com.ari.assistant.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Convierte el Markdown sencillo que escriben los modelos (títulos, listas,
 * negritas, cursivas, código) en texto con formato para mostrarlo en el chat.
 * No es un Markdown completo: cubre lo que Gemma y el equipo usan a diario.
 */
object Markdown {

    fun render(source: String): CharSequence {
        val out = SpannableStringBuilder()
        var inCode = false

        source.lines().forEachIndexed { index, rawLine ->
            if (index > 0) out.append('\n')
            val line = rawLine.trimEnd()

            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                // La línea de la valla no se muestra; se quita el salto que la precede.
                if (out.isNotEmpty() && out.last() == '\n') out.delete(out.length - 1, out.length)
                return@forEachIndexed
            }

            if (inCode) {
                val start = out.length
                out.append(line)
                out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(BackgroundColorSpan(AriTheme.codeBackground), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return@forEachIndexed
            }

            val heading = HEADING.find(line)
            if (heading != null) {
                val start = out.length
                appendInline(out, heading.groupValues[2])
                val size = when (heading.groupValues[1].length) {
                    1 -> 1.3f
                    2 -> 1.18f
                    else -> 1.08f
                }
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(size), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return@forEachIndexed
            }

            val bullet = BULLET.find(line)
            if (bullet != null) {
                out.append(bullet.groupValues[1]).append("•  ")
                out.setSpan(ForegroundColorSpan(AriTheme.ice), out.length - 3, out.length - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                appendInline(out, bullet.groupValues[2])
                return@forEachIndexed
            }

            appendInline(out, line)
        }
        return out
    }

    /** Negritas (**), cursivas (* o _) y código (`) dentro de una línea. */
    private fun appendInline(out: SpannableStringBuilder, text: String) {
        var i = 0
        while (i < text.length) {
            val match = INLINE.find(text, i)
            if (match == null) {
                out.append(text.substring(i))
                return
            }
            out.append(text.substring(i, match.range.first))
            val start = out.length
            when {
                match.groupValues[1].isNotEmpty() -> {
                    out.append(match.groupValues[1])
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                match.groupValues[2].isNotEmpty() -> {
                    out.append(match.groupValues[2])
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(AriTheme.codeBackground), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(ForegroundColorSpan(AriTheme.ice), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    out.append(match.groupValues[3].ifEmpty { match.groupValues[4] })
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            i = match.range.last + 1
        }
    }

    private val HEADING = Regex("^\\s{0,3}(#{1,3})\\s+(.+)$")
    private val BULLET = Regex("^(\\s*)[-*•]\\s+(.+)$")

    // **negrita** | `código` | *cursiva* | _cursiva_
    private val INLINE = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\w)|(?<!\\w)_(?!\\s)(.+?)(?<!\\s)_(?!\\w)")
}
