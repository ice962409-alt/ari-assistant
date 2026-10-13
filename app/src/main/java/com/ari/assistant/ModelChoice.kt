package com.ari.assistant

/**
 * Qué Gemma le conviene a cada teléfono. Ari mide la memoria RAM y el espacio libre
 * y recomienda el modelo que mejor corre ahí (sin Android, para poder probarlo).
 *
 * - Gemma 4 E4B: el más listo; pide un teléfono con 8 GB de RAM o más.
 * - Gemma 4 E2B: más ligero y rápido; para teléfonos con 4 a 6 GB de RAM.
 */
object ModelChoice {

    enum class Model(val id: String, val label: String, val url: String, val bytes: Long) {
        E4B(
            "e4b", "Gemma 4 E4B",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            3_659_530_240L
        ),
        E2B(
            "e2b", "Gemma 4 E2B",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            2_588_147_712L
        );

        /** El modelo más un margen para que el teléfono no se quede sin espacio. */
        val spaceNeeded: Long get() = bytes + 500_000_000L

        companion object {
            fun byId(id: String?): Model? = values().firstOrNull { it.id == id }
        }
    }

    /** Un teléfono "de 8 GB" le dice a Android unos 7.4: se cuenta desde 7. */
    const val FULL_RAM = 7L * GIB
    /** Con menos de esto, ni el modelo ligero corre bien. */
    const val MIN_RAM = 3L * GIB

    /** Lo que Ari recomienda para este teléfono y por qué. */
    data class Advice(val model: Model, val basic: Boolean, val tooWeak: Boolean, val reason: String)

    fun recommend(ramBytes: Long, freeBytes: Long): Advice {
        val ram = "${gb(ramBytes)} GB de RAM"
        val free = "${gb(freeBytes)} GB libres"
        return when {
            ramBytes >= FULL_RAM && freeBytes >= Model.E4B.spaceNeeded ->
                Advice(Model.E4B, basic = false, tooWeak = false, "Tu teléfono tiene $ram y $free: le va el modelo completo.")
            ramBytes >= FULL_RAM ->
                Advice(Model.E2B, basic = false, tooWeak = false,
                    "Tu teléfono tiene $ram, pero solo $free: el modelo completo no cabe, así que te recomiendo el ligero.")
            ramBytes >= MIN_RAM ->
                Advice(Model.E2B, basic = true, tooWeak = false,
                    "Tu teléfono tiene $ram: el modelo ligero corre mejor ahí. Ari usará su versión básica.")
            else ->
                Advice(Model.E2B, basic = true, tooWeak = true,
                    "Tu teléfono tiene $ram: es poca para Gemma. Puedes probar el modelo ligero, pero puede ir muy lento o cerrarse.")
        }
    }

    /**
     * Versión básica: en teléfonos con menos de 7 GB de RAM, Ari no carga la "vista"
     * (fotos, PDF, ver la pantalla), que ocupa mucha memoria; todo lo demás funciona.
     */
    fun basic(ramBytes: Long): Boolean = ramBytes in 1 until FULL_RAM

    fun gb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_000_000_000.0)
}

private const val GIB = 1024L * 1024 * 1024
