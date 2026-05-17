package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTO for chat message chunk from SSE stream.
 */
@Serializable
data class ChatStreamChunk(
    val type: String, // "text_chunk", "sentence_audio", "sentence_end", "stream_end"
    val content: String? = null, // Text content for text_chunk
    val audioData: String? = null, // Base64 encoded audio for sentence_audio
    val audioIndex: Int? = null, // Index for ordering audio clips
    val sentenceId: String? = null // Sentence identifier
)

/**
 * Wrapper for SSE event data parsing.
 */
@Serializable
data class SSEEventData(
    val type: String,
    val content: String? = null,
    val audioData: String? = null,
    val audioIndex: Int? = null
)

/**
 * STT multipart request part names.
 */
object SttParts {
    const val AUDIO = "audio"
    const val FORMAT = "format"
}