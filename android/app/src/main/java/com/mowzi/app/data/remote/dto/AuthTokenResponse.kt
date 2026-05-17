package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenResponse(
    val success: Boolean,
    val token: String? = null,
    val expiresAt: Long? = null
)