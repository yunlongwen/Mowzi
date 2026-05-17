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

@Serializable
data class NullableActiveConversationResponse(
    val id: String? = null,
    val characterId: String? = null,
    val title: String? = null,
    val status: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val lastMessageAt: Long? = null
)