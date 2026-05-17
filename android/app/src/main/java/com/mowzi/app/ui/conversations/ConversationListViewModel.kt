package com.mowzi.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowzi.app.data.local.entity.ConversationEntity
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.ConversationResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ConversationListUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val activeConversations: List<ConversationEntity> = emptyList(),
    val pausedConversations: List<ConversationEntity> = emptyList(),
    val archivedConversations: List<ConversationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val api: MowziApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Fetch active conversations
                val activeResponse = api.getConversations(status = "active")
                if (activeResponse.isSuccessful) {
                    val activeItems = activeResponse.body()?.items ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        activeConversations = activeItems.map { it.toEntity() }
                    )
                }

                // Fetch idle (paused) conversations
                val idleResponse = api.getConversations(status = "idle")
                if (idleResponse.isSuccessful) {
                    val idleItems = idleResponse.body()?.items ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        pausedConversations = idleItems.map { it.toEntity() }
                    )
                }

                // Fetch archived conversations
                val archivedResponse = api.getConversations(status = "archived")
                if (archivedResponse.isSuccessful) {
                    val archivedItems = archivedResponse.body()?.items ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        archivedConversations = archivedItems.map { it.toEntity() }
                    )
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载对话列表失败"
                )
            }
        }
    }

    fun resumeConversation(conversationId: Int) {
        viewModelScope.launch {
            try {
                val response = api.resumeConversation(conversationId)
                if (response.isSuccessful) {
                    loadConversations() // Reload to get updated list
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "恢复对话失败"
                )
            }
        }
    }

    private fun parseTimestamp(str: String): Long {
        return try {
            Instant.parse(str).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun ConversationResponse.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id.toString(),
            characterId = characterId.toString(),
            title = title ?: "新对话",
            status = status,
            createdAt = parseTimestamp(createdAt),
            updatedAt = parseTimestamp(updatedAt),
            lastMessageAt = parseTimestamp(lastMessageAt)
        )
    }
}