package com.ari.assistant.data

import android.content.Context
import androidx.room.withTransaction

/**
 * Repositorio local para operaciones de conversación e historial de mensajes.
 * 
 * Encapsula la lógica de acceso a Room y gestiona:
 * - Creación y recuperación de conversaciones actuales
 * - Guardado y recuperación de mensajes
 * - Persistencia de la conversación activa mediante SharedPreferences
 * 
 * No contiene lógica de IA ni integración con Gemma.
 */
class ConversationRepository(context: Context) {
    private val database = AriDatabase.getInstance(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences(
        PREF_FILE_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Recupera todos los mensajes de una conversación en orden cronológico.
     */
    suspend fun getMessages(conversationId: Long): List<ConversationMessageEntity> {
        return database.messageDao().getMessagesByConversation(conversationId)
    }

    /**
     * Guarda un nuevo mensaje en la base de datos.
     * Actualiza automáticamente el timestamp de la conversación.
     * 
     * @param conversationId ID de la conversación
     * @param role "user" o "assistant"
     * @param content Contenido del mensaje
     * @return El id del mensaje insertado
     */
    suspend fun saveMessage(
        conversationId: Long,
        role: String,
        content: String
    ): Long {
        val message = ConversationMessageEntity(
            conversationId = conversationId,
            role = role,
            content = content
        )

        return database.withTransaction {
            val messageId = database.messageDao().insert(message)
            // Actualizar timestamp de la conversación para marcarla como recientemente usada
            database.conversationDao().updateTimestamp(conversationId, System.currentTimeMillis())
            messageId
        }
    }

    /**
     * Obtiene o crea la conversación actual.
     * 
     * Si existe una conversación guardada en SharedPreferences y es válida en la BD,
     * la retorna. Si no existe, crea una nueva conversación.
     * 
     * @return El id de la conversación actual
     */
    suspend fun getOrCreateCurrentConversation(): Long {
        val storedId = prefs.getLong(PREF_CURRENT_CONVERSATION_ID, -1L)

        // Verificar si la conversación guardada aún existe en la BD
        if (storedId > 0L) {
            val conversation = database.conversationDao().getById(storedId)
            if (conversation != null) {
                return storedId
            }
        }

        // Si no existe, crear una nueva conversación
        val newConversation = ConversationEntity()
        val newId = database.conversationDao().insert(newConversation)
        prefs.edit().putLong(PREF_CURRENT_CONVERSATION_ID, newId).apply()
        return newId
    }

    /**
     * Crea una nueva conversación y la establece como actual.
     * Útil para iniciar un nuevo diálogo.
     * 
     * @return El id de la nueva conversación
     */
    suspend fun resetCurrentConversation(): Long {
        val newConversation = ConversationEntity()
        val newId = database.conversationDao().insert(newConversation)
        prefs.edit().putLong(PREF_CURRENT_CONVERSATION_ID, newId).apply()
        return newId
    }

    /**
     * Elimina todos los mensajes de la conversación actual, pero mantiene la conversación.
     * 
     * @return El id de la conversación actual
     */
    suspend fun clearCurrentConversationMessages(): Long {
        val currentId = getOrCreateCurrentConversation()
        database.messageDao().deleteByConversation(currentId)
        return currentId
    }

    /**
     * Obtiene todas las conversaciones ordenadas por fecha de actualización.
     */
    suspend fun getAllConversations(): List<ConversationEntity> {
        return database.conversationDao().getAll()
    }

    /**
     * Elimina una conversación específica (y todos sus mensajes).
     */
    suspend fun deleteConversation(conversationId: Long) {
        database.conversationDao().deleteById(conversationId)
    }

    companion object {
        private const val PREF_FILE_NAME = "ari_conversation_prefs"
        private const val PREF_CURRENT_CONVERSATION_ID = "current_conversation_id"
    }
}
