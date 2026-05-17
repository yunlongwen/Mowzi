package com.mowzi.app.data.repository

import com.mowzi.app.data.local.entity.CachedMessageEntity
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesByConversation(conversationId: String): Flow<List<CachedMessageEntity>>
    suspend fun insertMessage(message: CachedMessageEntity)
    suspend fun insertMessages(messages: List<CachedMessageEntity>)
    suspend fun upsertMessage(message: CachedMessageEntity)
}