package com.mowzi.app.data.repository

import com.mowzi.app.data.local.dao.ConversationDao
import com.mowzi.app.data.local.entity.ConversationEntity
import com.mowzi.app.data.remote.MowziApi
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val api: MowziApi
) : ConversationRepository {

    private val formatter = DateTimeFormatter.ISO_INSTANT

    private fun parseTimestamp(str: String): Long {
        return try {
            Instant.parse(str).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

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

    override suspend fun getConversationsByStatus(status: String): List<ConversationEntity> {
        // Fetch from API and cache locally
        val response = api.getConversations(status = status)
        if (response.isSuccessful) {
            val items = response.body()?.items ?: emptyList()
            val entities = items.map { dto ->
                ConversationEntity(
                    id = dto.id.toString(),
                    characterId = dto.characterId.toString(),
                    title = dto.title ?: "新对话",
                    status = dto.status,
                    createdAt = parseTimestamp(dto.createdAt),
                    updatedAt = parseTimestamp(dto.updatedAt),
                    lastMessageAt = parseTimestamp(dto.lastMessageAt)
                )
            }
            entities.forEach { conversationDao.upsert(it) }
            return entities
        }
        return emptyList()
    }

    override suspend fun refreshConversations() {
        // Refresh all conversations from API
        val activeResponse = api.getConversations(status = "active")
        if (activeResponse.isSuccessful) {
            activeResponse.body()?.items?.forEach { dto ->
                conversationDao.upsert(dto.toEntity())
            }
        }

        val idleResponse = api.getConversations(status = "idle")
        if (idleResponse.isSuccessful) {
            idleResponse.body()?.items?.forEach { dto ->
                conversationDao.upsert(dto.toEntity())
            }
        }

        val archivedResponse = api.getConversations(status = "archived")
        if (archivedResponse.isSuccessful) {
            archivedResponse.body()?.items?.forEach { dto ->
                conversationDao.upsert(dto.toEntity())
            }
        }
    }

    private fun com.mowzi.app.data.remote.dto.ConversationResponse.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id.toString(),
            characterId = characterId.toString(),
            title = title ?: "新对话",
            status = status,
            createdAt = parseTimestamp(createdAt),
            updatedAt = parseTimestamp(updatedAt),
            lastMessageAt = parseTimestamp(lastMessageAt)
        )
    }
}