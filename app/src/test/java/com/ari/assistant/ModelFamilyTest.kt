package com.ari.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelFamilyTest {

    @Test
    fun detectsTheModelByItsName() {
        assertEquals(ModelFamily.GEMMA, ModelFamily.detect("gemma-4-E4B-it.litertlm"))
        assertEquals(ModelFamily.GEMMA, ModelFamily.detect(null))
        assertEquals(ModelFamily.QWEN, ModelFamily.detect("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"))
        assertEquals(ModelFamily.LLAMA, ModelFamily.detect("Llama-3.2-1B.litertlm"))
        assertEquals(ModelFamily.OTHER, ModelFamily.detect("modelo.litertlm"))
    }

    @Test
    fun onlyGemmaSees() {
        assertEquals(true, ModelFamily.GEMMA.vision)
        assertEquals(false, ModelFamily.QWEN.vision)
    }

    @Test
    fun explainsFormatsAriCannotLoad() {
        assertNotNull(ModelFamily.unsupportedFormat("Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf"))
        assertNull(ModelFamily.unsupportedFormat("Qwen3-0.6B.litertlm"))
    }

    @Test
    fun qwenDownloadsAreQwenAndNeverRecommendedOverGemma() {
        assertEquals(ModelFamily.QWEN, ModelChoice.Model.QWEN15.family)
        assertEquals(ModelFamily.QWEN, ModelChoice.Model.byId("qwen06")?.family)
        assertEquals(ModelFamily.GEMMA, ModelChoice.Model.E4B.family)
        // Ari sigue recomendando Gemma (la que ve y es más lista); Qwen se elige a propósito.
        val advice = ModelChoice.recommend(12L * 1024 * 1024 * 1024, 20L * 1024 * 1024 * 1024)
        assertEquals(ModelFamily.GEMMA, advice.model.family)
    }
}
