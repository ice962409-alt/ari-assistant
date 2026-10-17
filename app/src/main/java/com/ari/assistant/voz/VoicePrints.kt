package com.ari.assistant.voz

import kotlin.math.sqrt

/** La huella de voz de una persona: varias muestras (una por frase que leyó al registrarse). */
data class VoicePrint(val name: String, val owner: Boolean, val samples: List<FloatArray>)

/**
 * Compara huellas de voz, sin Android (para probarlo). Una huella es una lista de
 * números; dos huellas de la misma persona "apuntan" casi a la misma dirección.
 * Eso se mide con la similitud coseno: 1 es idéntica, 0 no se parecen.
 */
object VoicePrints {

    /**
     * Qué tan parecidas deben ser para decir "es la misma persona". Si es muy alto, a
     * veces no te reconoce; si es muy bajo, podría confundirte con otra persona
     * (falso positivo). Se ajustará con tus pruebas.
     */
    const val THRESHOLD = 0.55f

    /** Margen sobre la segunda más parecida: si dos personas empatan, Ari no adivina. */
    const val MARGIN = 0.05f

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /**
     * Cuánto le puede faltar a la voz del dueño para seguir siendo él cuando es la única voz
     * registrada y venía hablando él (o apenas empieza): de cerca o de lejos la misma voz da
     * números distintos, y no debe volverse "voz nueva".
     */
    const val OWNER_DOUBT = 0.15f

    /**
     * Qué tanto se parece [embedding] a la persona: el promedio de la mitad de sus muestras que
     * más se parecen (así una frase dicha de lejos se compara con las muestras más parecidas,
     * no con todas).
     */
    fun score(embedding: FloatArray, print: VoicePrint): Float {
        if (print.samples.isEmpty()) return 0f
        val sorted = print.samples.map { cosine(embedding, it) }.sortedDescending()
        return sorted.take((sorted.size + 1) / 2).average().toFloat()
    }

    /**
     * La persona que habla, o null si no se parece lo suficiente a nadie.
     * [ownerBefore]: true si venía hablando el dueño, null si aún no habla nadie, false si otra persona.
     */
    fun identify(
        embedding: FloatArray,
        prints: List<VoicePrint>,
        threshold: Float = THRESHOLD,
        ownerBefore: Boolean? = false
    ): VoicePrint? {
        val ranked = prints.map { it to score(embedding, it) }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        val onlyOwner = prints.all { it.owner }
        val needed = if (onlyOwner && ownerBefore != false) threshold - OWNER_DOUBT else threshold
        if (best.second < needed) return null
        val second = ranked.getOrNull(1)?.second ?: -1f
        if (best.second - second < MARGIN) return null
        return best.first
    }

    /** Para guardar: los números como bytes (4 por número). */
    fun toBytes(values: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(values.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }
}
