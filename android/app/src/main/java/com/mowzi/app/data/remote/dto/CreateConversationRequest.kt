package com.mowzi.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateConversationRequest(
    @SerialName("character_id")
    val characterId: String,
    val title: String? = null
)