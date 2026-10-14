package com.ari.assistant

import com.ari.assistant.agents.normalizeForAgents

/**
 * Solo en la ARI del dueño: ARI revisa su propia interfaz y su icono y propone
 * mejoras concretas. Como Gemma no ve la pantalla, aquí se le describe cómo es
 * ARI hoy; sus ideas salen como líneas "MEJORA: [Diseño] …" y, con "propón
 * mejoras", se vuelven fichas en GitHub para que Claude las programe.
 * (Sin Android, para probarlo.)
 */
object AriDesign {

    const val TAG = "[Diseño] "

    /** Cómo se ve ARI hoy, en palabras, para que Gemma razone sobre su diseño. */
    val CONTEXT: String =
        "Así es tu interfaz ahora mismo:\n" +
            "- Una pantalla de chat oscura. Tus mensajes salen en burbujas y arriba está tu carita (una esfera " +
            "de color hielo con ojos y boca que se mueve al hablar, pensar y escuchar).\n" +
            "- Abajo, una caja de texto, un botón de enviar y un botón de micrófono. También un botón \"Memoria\".\n" +
            "- Colores: fondo casi negro, acentos en azul hielo; hay seis colores para elegir.\n" +
            "- Ajustes es una lista simple de opciones.\n" +
            "- Una burbuja flotante con tu carita que se puede poner sobre otras apps y esconderse en el borde.\n" +
            "- Tu icono: un anillo de color hielo con un punto en medio y un pequeño satélite.\n" +
            "- Hay un widget aparte (Ari Estado) con una barra de progreso."

    /** La instrucción para Gemma cuando el dueño pide mejorar el diseño. */
    fun instruction(): String =
        "Actúa como diseñador de interfaces. Revisa tu propia apariencia (la pantalla, los colores, la carita, " +
            "la burbuja y el icono) y propón de 2 a 3 mejoras concretas y realistas para una app de Android, una por línea, " +
            "cada una empezando con \"MEJORA: $TAG\" y explicando en pocas palabras qué cambiar y por qué se vería o se " +
            "usaría mejor. No inventes funciones nuevas: solo mejoras de diseño y de cómo se ve o se siente. Sé específico " +
            "(por ejemplo: tamaños, espacios, animaciones suaves, contraste, forma del icono), no digas cosas vagas."

    /** "mejora tu interfaz", "propón mejoras de diseño", "cómo te verías mejor", "mejora tu icono". */
    fun asks(text: String): Boolean {
        val t = normalizeForAgents(text)
        return Regex(
            "^(?:mejora|mejorate|redisena|cambia|revisa)(?:\\s+(?:tu|el|la))?\\s+(?:interfaz|diseno|apariencia|icono|imagen|carita|aspecto)" +
                "|^(?:propon|proponme|dame|arma)\\s+(?:unas?\\s+)?(?:mejoras?|ideas?|propuestas?)\\s+(?:de|para)\\s+(?:el\\s+)?(?:diseno|interfaz|icono|apariencia)" +
                "|^como\\s+(?:te\\s+)?(?:verias|te\\s+verias|mejorarias)\\s+(?:mejor|tu\\s+(?:interfaz|diseno|icono))" +
                "|^que\\s+(?:le\\s+)?(?:cambiarias|mejorarias)\\s+(?:a\\s+)?(?:tu|la)\\s+(?:interfaz|diseno|icono|apariencia)$"
        ).containsMatchIn(t)
    }
}
