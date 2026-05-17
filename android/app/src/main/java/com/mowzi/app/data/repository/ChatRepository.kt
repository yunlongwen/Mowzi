package com.mowzi.app.data.repository

import com.mowzi.app.data.local.dao.MessageDao
import com.mowzi.app.data.local.entity.CachedMessageEntity
import com.mowzi.app.data.remote.dto.ChatStreamChunk
import com.mowzi.app.data.remote.dto.ChatStreamRequest
import com.mowzi.app.sse.SSEClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val sseClient: SSEClient,
    private val messageDao: MessageDao,
    private val json: Json,
    private val baseUrl: String
) {
    fun streamChat(
        conversationId: String,
        characterId: String,
        text: String
    ): Flow<ChatStreamChunk> = flow {
        val request = ChatStreamRequest(
            conversationId = conversationId,
            characterId = characterId,
            text = text
        )

        val jsonBody = json.encodeToString(ChatStreamRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/v1/chat/stream")
            .post(jsonBody)
            .build()

        sseClient.connect(httpRequest).collect { event ->
            try {
                val chunk = json.decodeFromString<ChatStreamChunk>(event.data)
                emit(chunk)
            } catch (e: Exception) {
                // Ignore malformed events
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveMessage(message: CachedMessageEntity) = withContext(Dispatchers.IO) {
        messageDao.upsert(message)
    }

    fun getMessages(conversationId: String): Flow<List<CachedMessageEntity>> {
        return messageDao.getByConversation(conversationId)
    }
}
