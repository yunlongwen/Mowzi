package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterRequest(
    val deviceId: String,
    val deviceName: String,
    val deviceModel: String
)