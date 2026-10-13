package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Los colores que puede tener Ari. Cambian su esfera y los detalles de la app.
 * Se eligen en Ajustes → Color de Ari, o diciéndole "ponte rosa" (sin Android, para probarlo).
 */
object AriColors {

    /** [light] brillo de arriba, [main] el color, [deep] la orilla. */
    data class Palette(val id: String, val name: String, val light: Int, val main: Int, val deep: Int, val words: List<String>)

    private fun rgb(hex: Long): Int = (0xFF000000 or hex).toInt()

    val ALL = listOf(
        Palette("hielo", "Hielo", rgb(0xDEF6FD), rgb(0x9ED8EE), rgb(0x5FB8DA), listOf("hielo", "azul", "celeste", "normal", "original")),
        Palette("rosa", "Rosa", rgb(0xFDE4EE), rgb(0xF4A6C0), rgb(0xD9729A), listOf("rosa", "rosado", "rosita")),
        Palette("lila", "Lila", rgb(0xEEE6FD), rgb(0xC3AEF4), rgb(0x9479D9), listOf("lila", "morado", "violeta", "lavanda")),
        Palette("menta", "Menta", rgb(0xDDF8EC), rgb(0x9EE6C4), rgb(0x5CC49A), listOf("menta", "verde")),
        Palette("durazno", "Durazno", rgb(0xFDEBDF), rgb(0xF7BE9C), rgb(0xE08F64), listOf("durazno", "naranja", "anaranjado", "coral")),
        Palette("dorado", "Dorado", rgb(0xFDF4D6), rgb(0xF2D27C), rgb(0xD2A93E), listOf("dorado", "oro", "amarillo")),
    )

    val DEFAULT = ALL.first()

    fun byId(id: String?): Palette = ALL.firstOrNull { it.id == id } ?: DEFAULT

    private val ASK = Regex(
        "^(?:(?:cambia|cambiate|pon|ponte|pinta|pintate|vuelvete|hazte)\\s+)" +
            "(?:(?:tu|el)\\s+color\\s+)?(?:a\\s+|de\\s+|en\\s+)?(?:color\\s+)?(\\p{L}+)$"
    )

    /** "Ponte rosa", "cambia tu color a lila", "píntate de verde": el color pedido, o null. */
    fun requested(text: String): Palette? {
        val clean = normalizeForAgents(text)
        val word = ASK.find(clean)?.groupValues?.get(1) ?: return null
        return ALL.firstOrNull { word in it.words }
    }
}
