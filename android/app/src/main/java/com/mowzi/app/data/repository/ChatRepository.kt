package com.mowzi.app.data.repository

import com.mowzi.app.audio.OpusEncoder
import com.mowzi.app.data.local.dao.MessageDao
import com.mowzi.app.data.local.entity.CachedMessageEntity
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.ChatStreamChunk
import com.mowzi.app.data.remote.dto.ChatStreamRequest
import com.mowzi.app.sse.SSEClient
import com.mowzi.app.sse.SSEEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for chat operations including STT and streaming.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val api: MowziApi,
    private val sseClient: SSEClient,
    private val messageDao: MessageDao,
    private val json: Json,
    private val baseUrl: String
) {
    private val opusEncoder = OpusEncoder()

    /**
     * Converts PCM audio data to Opus format using the Concentus Opus encoder.
     */
    suspend fun pcmToOpus(pcmData: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val output = ByteArrayOutputStream()

        // Encode in 60ms frames (960 samples at 16kHz)
        val frameSize = 960
        var offset = 0

        while (offset + frameSize * 2 <= pcmData.size) {
            val frameData = pcmData.copyOfRange(offset, offset + frameSize * 2)
            val encoded = opusEncoder.encodeFromBytes(frameData)
            output.write(encoded)
            offset += frameSize * 2
        }

        output.toByteArray()
    }

    /**
     * Performs speech-to-text on audio data.
     * @param audioData PCM audio bytes
     * @return Recognized text
     */
    suspend fun speechToText(audioData: ByteArray): String = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("format", "pcm")
            .addFormDataPart(
                "audio",
                "audio.pcm",
                audioData.toRequestBody("audio/pcm".toMediaType())
            )
            .build()

        val response = api.speechToText(
            audio = MultipartBody.Part.create(requestBody),
            format = "pcm"
        )

        if (response.isSuccessful) {
            response.body()?.text ?: ""
        } else {
            throw Exception("STT failed: ${response.code()}")
        }
    }

    /**
     * Streams chat responses as SSE events.
     */
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

    /**
     * Saves a message to local cache.
     */
    suspend fun saveMessage(message: CachedMessageEntity) = withContext(Dispatchers.IO) {
        messageDao.upsert(message)
    }

    /**
     * Gets messages for a conversation.
     */
    fun getMessages(conversationId: String): Flow<List<CachedMessageEntity>> {
        return messageDao.getByConversation(conversationId)
    }
}