package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatStreamChunk(
    val type: String, // "text_chunk", "text_done", "done", "error", "stream_end"
    val content: String? = null
)
