package com.mowzi.app.data.repository

import com.mowzi.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    suspend fun getActiveConversation(): ConversationEntity?
    fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun upsertConversation(conversation: ConversationEntity)
    suspend fun updateConversationStatus(id: String, status: String)
}