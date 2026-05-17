package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ParentSettingsResponse(
    val dailyLimitMin: Int = 60,
    val sessionLimitMin: Int = 30,
    val blockedHoursStart: String? = null,
    val blockedHoursEnd: String? = null,
    val llmApiUrl: String? = null,
    val llmModel: String? = null,
    val xfyunAppId: String? = null
)

@Serializable
data class ParentSettingsRequest(
    val dailyLimitMin: Int? = null,
    val sessionLimitMin: Int? = null,
    val blockedHoursStart: String? = null,
    val blockedHoursEnd: String? = null,
    val llmApiUrl: String? = null,
    val llmApiKey: String? = null,
    val llmModel: String? = null,
    val xfyunAppId: String? = null,
    val xfyunApiKey: String? = null,
    val xfyunApiSecret: String? = null
)

@Serializable
data class ParentUsageItem(
    val date: String,
    val minutes: Int,
    val messageCount: Int
)

@Serializable
data class ParentUsageResponse(
    val usage: List<ParentUsageItem>
)

@Serializable
data class ParentConversationDto(
    val id: String,
    val characterName: String,
    val title: String,
    val messageCount: Int,
    val createdAt: Long,
    val lastMessageAt: Long? = null
)

@Serializable
data class ParentConversationsResponse(
    val conversations: List<ParentConversationDto>
)

@Serializable
data class ParentMessageDto(
    val role: String,
    val content: String,
    val timestamp: Long
)

@Serializable
data class ParentMessagesResponse(
    val messages: List<ParentMessageDto>
)