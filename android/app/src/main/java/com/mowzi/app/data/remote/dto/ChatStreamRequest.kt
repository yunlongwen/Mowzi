package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatStreamRequest(
    val conversationId: String,
    val characterId: String,
    val text: String
)