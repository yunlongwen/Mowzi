package com.mowzi.app.data.repository

import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.PinRequest
import com.mowzi.app.data.remote.dto.ParentConversationDto
import com.mowzi.app.data.remote.dto.ParentMessageDto
import com.mowzi.app.data.remote.dto.ParentSettingsRequest
import com.mowzi.app.data.remote.dto.ParentSettingsResponse
import com.mowzi.app.data.remote.dto.ParentUsageItem
import com.mowzi.app.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ParentRepository for parent control panel operations.
 */
@Singleton
class ParentRepositoryImpl @Inject constructor(
    private val api: MowziApi,
    private val tokenManager: TokenManager
) : ParentRepository {

    override suspend fun authenticate(pin: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.parentAuth(PinRequest(pin))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.token != null) {
                    tokenManager.saveParentToken(body.token)
                    Result.success(body.token)
                } else {
                    Result.failure(Exception("Authentication failed"))
                }
            } else {
                Result.failure(Exception("PIN码错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSettings(): Result<ParentSettingsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getParentSettings()
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("Failed to get settings: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSettings(request: ParentSettingsRequest): Result<ParentSettingsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.updateParentSettings(request)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("Failed to update settings: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUsage(period: String): Result<List<ParentUsageItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getParentUsage(period)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it.usage)
                } ?: Result.success(emptyList())
            } else {
                Result.failure(Exception("Failed to get usage: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConversations(): Result<List<ParentConversationDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getParentConversations()
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it.conversations)
                } ?: Result.success(emptyList())
            } else {
                Result.failure(Exception("Failed to get conversations: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMessages(conversationId: String): Result<List<ParentMessageDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getParentMessages(conversationId)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it.messages)
                } ?: Result.success(emptyList())
            } else {
                Result.failure(Exception("Failed to get messages: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}