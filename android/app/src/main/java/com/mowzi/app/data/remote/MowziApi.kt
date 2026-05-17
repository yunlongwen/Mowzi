package com.mowzi.app.data.remote

import com.mowzi.app.data.remote.dto.ActiveConversationResponse
import com.mowzi.app.data.remote.dto.NullableActiveConversationResponse
import com.mowzi.app.data.remote.dto.AuthTokenResponse
import com.mowzi.app.data.remote.dto.CharactersResponse
import com.mowzi.app.data.remote.dto.ChatStreamRequest
import com.mowzi.app.data.remote.dto.ConversationListResponse
import com.mowzi.app.data.remote.dto.ConversationResponse
import com.mowzi.app.data.remote.dto.CreateConversationRequest
import com.mowzi.app.data.remote.dto.DeviceRegisterRequest
import com.mowzi.app.data.remote.dto.DeviceRegisterResponse
import com.mowzi.app.data.remote.dto.PinRequest
import com.mowzi.app.data.remote.dto.ResumeConversationResponse
import com.mowzi.app.data.remote.dto.ParentSettingsResponse
import com.mowzi.app.data.remote.dto.ParentSettingsRequest
import com.mowzi.app.data.remote.dto.ParentUsageResponse
import com.mowzi.app.data.remote.dto.ParentConversationsResponse
import com.mowzi.app.data.remote.dto.ParentMessagesResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.PUT

interface MowziApi {
    @Streaming
    @POST("/api/v1/chat/stream")
    fun chatStream(@Body request: ChatStreamRequest): retrofit2.Call<ResponseBody>

    @POST("/api/v1/conversations")
    suspend fun createConversation(
        @Body request: CreateConversationRequest
    ): Response<ConversationResponse>

    @GET("/api/v1/conversations/active")
    suspend fun getActiveConversationRaw(): Response<ResponseBody>

    @GET("/api/v1/conversations")
    suspend fun getConversations(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): Response<ConversationListResponse>

    @PUT("/api/v1/conversations/{conversationId}/resume")
    suspend fun resumeConversation(
        @Path("conversationId") conversationId: Int
    ): Response<ResumeConversationResponse>

    @GET("/api/v1/parent/settings")
    suspend fun getParentSettings(): Response<ParentSettingsResponse>

    @PUT("/api/v1/parent/settings")
    suspend fun updateParentSettings(
        @Body request: ParentSettingsRequest
    ): Response<ParentSettingsResponse>

    @GET("/api/v1/parent/usage")
    suspend fun getParentUsage(
        @Query("period") period: String = "daily"
    ): Response<ParentUsageResponse>

    @GET("/api/v1/parent/conversations")
    suspend fun getParentConversations(): Response<ParentConversationsResponse>

    @GET("/api/v1/parent/conversations/{conversationId}/messages")
    suspend fun getParentMessages(
        @Path("conversationId") conversationId: String
    ): Response<ParentMessagesResponse>

    @GET("/api/v1/config/characters")
    suspend fun getCharacters(): Response<CharactersResponse>

    @POST("/api/v1/device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<DeviceRegisterResponse>

    @POST("/api/v1/parent/auth")
    suspend fun parentAuth(@Body request: PinRequest): Response<AuthTokenResponse>
}
