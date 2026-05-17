package com.mowzi.app.data.remote

import com.mowzi.app.data.remote.dto.ActiveConversationResponse
import com.mowzi.app.data.remote.dto.AuthTokenResponse
import com.mowzi.app.data.remote.dto.CharactersResponse
import com.mowzi.app.data.remote.dto.ChatStreamRequest
import com.mowzi.app.data.remote.dto.ConversationResponse
import com.mowzi.app.data.remote.dto.CreateConversationRequest
import com.mowzi.app.data.remote.dto.DeviceRegisterRequest
import com.mowzi.app.data.remote.dto.DeviceRegisterResponse
import com.mowzi.app.data.remote.dto.PinRequest
import com.mowzi.app.data.remote.dto.SttResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface MowziApi {
    @Multipart
    @POST("/api/v1/chat/stt")
    suspend fun speechToText(
        @Part audio: MultipartBody.Part,
        @Part("format") format: String
    ): Response<SttResponse>

    @Streaming
    @POST("/api/v1/chat/stream")
    fun chatStream(@Body request: ChatStreamRequest): retrofit2.Call<ResponseBody>

    @POST("/api/v1/conversations")
    suspend fun createConversation(
        @Body request: CreateConversationRequest
    ): Response<ConversationResponse>

    @GET("/api/v1/conversations/active")
    suspend fun getActiveConversation(): Response<ActiveConversationResponse?>

    @GET("/api/v1/config/characters")
    suspend fun getCharacters(): Response<CharactersResponse>

    @POST("/api/v1/device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<DeviceRegisterResponse>

    @POST("/api/v1/parent/auth")
    suspend fun parentAuth(@Body request: PinRequest): Response<AuthTokenResponse>
}