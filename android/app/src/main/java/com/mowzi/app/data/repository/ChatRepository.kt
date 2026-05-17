package com.mowzi.app.data.repository

import android.util.Log
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
    companion object {
        private const val TAG = "wyl"
    }

    fun streamChat(
        conversationId: String,
        characterId: String,
        text: String
    ): Flow<ChatStreamChunk> = flow {
        Log.d(TAG, "ChatRepository.streamChat: starting, conversationId=$conversationId, characterId=$characterId, text=$text")
        val request = ChatStreamRequest(
            conversationId = conversationId,
            characterId = characterId,
            text = text
        )

        val jsonBody = json.encodeToString(ChatStreamRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        Log.d(TAG, "ChatRepository.streamChat: request built, url=$baseUrl/api/v1/chat/stream")

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/v1/chat/stream")
            .post(jsonBody)
            .build()

        try {
            sseClient.connect(httpRequest).collect { event ->
                Log.d(TAG, "ChatRepository.streamChat: received SSE event=${event.event}, data=${event.data.take(100)}")
                try {
                    val chunk = json.decodeFromString<ChatStreamChunk>(event.data)
                    emit(chunk)
                } catch (e: Exception) {
                    Log.e(TAG, "ChatRepository.streamChat: failed to decode chunk", e)
                }
            }
            Log.d(TAG, "ChatRepository.streamChat: collect completed")
        } catch (e: Exception) {
            Log.e(TAG, "ChatRepository.streamChat: exception during streaming", e)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveMessage(message: CachedMessageEntity) = withContext(Dispatchers.IO) {
        messageDao.upsert(message)
    }

    fun getMessages(conversationId: String): Flow<List<CachedMessageEntity>> {
        return messageDao.getByConversation(conversationId)
    }
}
