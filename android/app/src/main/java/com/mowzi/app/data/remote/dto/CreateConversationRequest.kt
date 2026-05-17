package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateConversationRequest(
    val characterId: String,
    val title: String? = null
)