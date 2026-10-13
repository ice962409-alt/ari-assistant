package com.ari.assistant.agents

import com.ari.assistant.Memory

/**
 * Lo que el agente de memoria necesita de la base de datos. Así el agente se
 * puede probar sin un teléfono, con una memoria de mentira.
 */
interface MemoryStore {
    fun addMemory(content: String): Long
    fun memories(): List<Memory>
    fun deleteMemoriesMatching(text: String): Int
}
