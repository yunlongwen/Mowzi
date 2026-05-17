package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterResponse(
    val success: Boolean,
    val deviceToken: String? = null
)