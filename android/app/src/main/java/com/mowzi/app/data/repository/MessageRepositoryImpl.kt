package com.mowzi.app.data.repository

import com.mowzi.app.data.local.dao.MessageDao
import com.mowzi.app.data.local.entity.CachedMessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun getMessagesByConversation(conversationId: String): Flow<List<CachedMessageEntity>> {
        return messageDao.getByConversation(conversationId)
    }

    override suspend fun insertMessage(message: CachedMessageEntity) {
        messageDao.insert(message)
    }

    override suspend fun insertMessages(messages: List<CachedMessageEntity>) {
        messageDao.insertAll(messages)
    }

    override suspend fun upsertMessage(message: CachedMessageEntity) {
        messageDao.upsert(message)
    }
}