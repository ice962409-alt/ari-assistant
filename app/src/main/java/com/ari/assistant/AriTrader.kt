package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Lo propio de ARI Trader (la copia para el hermano del dueño): cómo se presenta y enseña a
 * usarse, el consejo para mejorar el modelo con ayuda de su computadora, y el texto de aviso
 * de actualización. Lógica pura, sin Android, para poder probarla.
 */
object AriTrader {

    /** Se presenta y enseña a usarse (trading, pero como entrenadora, no asesora). */
    fun intro(userName: String): String = buildString {
        val hi = if (userName.isBlank()) "¡Hola! Soy Ari Trader" else "¡Hola, $userName! Soy Ari Trader"
        append(hi).append(", tu entrenadora de trading. Todo lo hago aquí en tu teléfono: tus pláticas no salen de él.\n\n")
        append("Algo importante de una vez: **no doy consejos financieros ni te digo qué comprar o vender**, ")
        append("no tengo precios en vivo y el trading puede hacer perder dinero. Te ayudo a **aprender y a tener disciplina**, tú decides.\n\n")
        append("Esto es lo que puedo hacer por ti:\n\n")
        append("**Enseñarte trading a tu ritmo**: dime \"quiero aprender trading\" (o un tema: \"enséñame velas japonesas\", ")
        append("\"qué es la gestión de riesgo\"). Te explico paso a paso y recuerdo dónde nos quedamos (\"sigamos\").\n\n")
        append("**Platicar y resolver dudas**: pregúntame lo que no entiendas de un concepto.\n\n")
        append("**Recordatorios y notas**: \"recuérdame a las 8 revisar el mercado\", \"anota que no opero sin stop\".\n\n")
        append("**Hacerme mejor**: cuando algo no pueda hacer, dímelo con \"anota una mejora: …\". Cuando digas ")
        append("\"propón mejoras\" se lo mando a quien me cuida para que lo revise y me actualice.\n\n")
        append("Escribe **\"enséñame\"** y te llevo paso a paso, o **\"¿qué puedes hacer?\"** para volver a ver esto. ")
        append("Pronto tendré diario de operaciones y podré leer tus gráficas.")
    }

    /** Versión corta para decirla en voz. */
    fun spoken(): String =
        "Soy tu entrenadora de trading. Te enseño a tu ritmo y te ayudo con la disciplina, pero no doy consejos " +
            "ni señales: tú decides. Dime \"quiero aprender trading\" para empezar. En la pantalla te dejé la lista."

    /** Consejo del modelo: con una computadora puede bajar un modelo más listo para respuestas mejores. */
    val MODEL_TIP: String =
        "Un consejo: entre más \"listo\" sea el modelo que uso, mejores respuestas te doy. Si tienes una computadora, " +
            "puedes bajar un modelo más grande por Wi-Fi (toca Modelo y elige el que te recomiendo según tu teléfono), " +
            "o bajar el archivo .litertlm en la compu y pasármelo con \"Importar modelo\". Yo me adapto al que pongas."

    /** "cómo mejoro el modelo", "modelo más listo", "mejor modelo". */
    fun asksModel(text: String): Boolean {
        val t = normalizeForAgents(text)
        return Regex("(?:como\\s+)?(?:mejoro|mejorar|cambio|cambiar|pongo|poner|bajo|bajar|descargo|descargar)\\s+(?:un\\s+|el\\s+|mi\\s+)?(?:mejor\\s+)?modelo" +
            "|modelo\\s+(?:mas\\s+)?(?:listo|grande|mejor|potente)" +
            "|mejor\\s+modelo").containsMatchIn(t)
    }
}
