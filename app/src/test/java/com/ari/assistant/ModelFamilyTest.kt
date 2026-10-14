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
}
