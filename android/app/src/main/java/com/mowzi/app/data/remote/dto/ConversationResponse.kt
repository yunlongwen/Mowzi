package com.mowzi.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationResponse(
    val id: String,
    @SerialName("character_id")
    val characterId: String,
    val title: String,
    val status: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("last_message_at")
    val lastMessageAt: String = ""
)