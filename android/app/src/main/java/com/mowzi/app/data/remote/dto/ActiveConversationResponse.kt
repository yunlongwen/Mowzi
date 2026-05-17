package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ActiveConversationResponse(
    val id: String,
    val characterId: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long
)