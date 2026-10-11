package com.ari.assistant.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad que representa una conversación.
 * Cada conversación puede contener múltiples mensajes.
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "Conversación",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
