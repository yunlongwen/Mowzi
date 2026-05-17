package com.mowzi.app.data.repository

import com.mowzi.app.data.remote.dto.ParentConversationDto
import com.mowzi.app.data.remote.dto.ParentMessageDto
import com.mowzi.app.data.remote.dto.ParentSettingsRequest
import com.mowzi.app.data.remote.dto.ParentSettingsResponse
import com.mowzi.app.data.remote.dto.ParentUsageItem

/**
 * Repository interface for parent control panel operations.
 */
interface ParentRepository {
    /**
     * Authenticate with PIN code.
     * @param pin 4-digit PIN code
     * @return true if authentication successful
     */
    suspend fun authenticate(pin: String): Result<String>

    /**
     * Get parent settings.
     * @return Parent settings or error
     */
    suspend fun getSettings(): Result<ParentSettingsResponse>

    /**
     * Update parent settings.
     * @param request Settings update request
     * @return Updated settings or error
     */
    suspend fun updateSettings(request: ParentSettingsRequest): Result<ParentSettingsResponse>

    /**
     * Get usage statistics.
     * @param period "daily" or "weekly"
     * @return List of usage items or error
     */
    suspend fun getUsage(period: String): Result<List<ParentUsageItem>>

    /**
     * Get conversation list for parent view.
     * @return List of conversations or error
     */
    suspend fun getConversations(): Result<List<ParentConversationDto>>

    /**
     * Get messages for a specific conversation.
     * @param conversationId Conversation ID
     * @return List of messages or error
     */
    suspend fun getMessages(conversationId: String): Result<List<ParentMessageDto>>
}