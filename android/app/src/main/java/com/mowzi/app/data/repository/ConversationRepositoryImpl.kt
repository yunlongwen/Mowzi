package com.mowzi.app.data.repository

import com.mowzi.app.data.local.dao.ConversationDao
import com.mowzi.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao
) : ConversationRepository {

    override suspend fun getActiveConversation(): ConversationEntity? {
        return conversationDao.getActive()
    }

    override fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAll()
    }

    override suspend fun upsertConversation(conversation: ConversationEntity) {
        conversationDao.upsert(conversation)
    }

    override suspend fun updateConversationStatus(id: String, status: String) {
        conversationDao.updateStatus(id, status, System.currentTimeMillis())
    }
}