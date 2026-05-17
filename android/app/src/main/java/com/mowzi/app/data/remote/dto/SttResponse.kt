package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SttResponse(
    val text: String,
    val duration: Float? = null
)