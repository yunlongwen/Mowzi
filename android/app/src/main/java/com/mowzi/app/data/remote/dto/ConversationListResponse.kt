package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConversationListResponse(
    val items: List<ConversationResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

@Serializable
data class ResumeConversationResponse(
    val id: Int,
    val childId: Int,
    val characterId: Int,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long
)