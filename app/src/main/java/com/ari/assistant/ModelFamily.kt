package com.ari.assistant

/**
 * Qué modelo cargaste (por el nombre del archivo), para que ARI se adapte:
 * cómo se llama al mostrarlo y si puede ver imágenes. Sin Android, para probarlo.
 */
enum class ModelFamily(val label: String, val vision: Boolean) {
    GEMMA("Gemma", true),
    QWEN("Qwen", false),
    LLAMA("Llama", false),
    PHI("Phi", false),
    DEEPSEEK("DeepSeek", false),
    OTHER("el modelo", false);

    companion object {
        fun detect(fileName: String?): ModelFamily {
            val name = fileName.orEmpty().lowercase()
            return when {
                name.isEmpty() || "gemma" in name -> GEMMA
                "qwen" in name -> QWEN
                "llama" in name -> LLAMA
                "phi" in name -> PHI
                "deepseek" in name -> DEEPSEEK
                else -> OTHER
            }
        }

        /** ARI carga modelos .litertlm (y .task). Los .gguf necesitan otro motor que aún no tiene. */
        fun unsupportedFormat(fileName: String?): String? {
            val name = fileName.orEmpty().lowercase()
            return when {
                name.endsWith(".gguf") ->
                    "Ese archivo es .gguf (para apps como PocketPal). ARI todavía no lee ese formato: busca la versión .litertlm del modelo (por ejemplo en huggingface.co/litert-community)."
                name.endsWith(".zip") || name.endsWith(".safetensors") || name.endsWith(".bin") ->
                    "Ese archivo no es un modelo que ARI pueda cargar. Necesito un archivo .litertlm."
                else -> null
            }
        }
    }
}
