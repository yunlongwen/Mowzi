package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PinRequest(
    val pin: String
)