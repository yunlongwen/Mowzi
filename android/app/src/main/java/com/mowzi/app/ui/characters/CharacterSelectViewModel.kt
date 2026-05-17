package com.mowzi.app.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.CharacterDto
import com.mowzi.app.data.remote.dto.CreateConversationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharacterSelectUiState(
    val characters: List<CharacterDto> = emptyList(),
    val isLoading: Boolean = false,
    val selectedCharacterId: String? = null,
    val createdConversationId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class CharacterSelectViewModel @Inject constructor(
    private val api: MowziApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterSelectUiState())
    val uiState: StateFlow<CharacterSelectUiState> = _uiState.asStateFlow()

    init {
        loadCharacters()
    }

    fun loadCharacters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = api.getCharacters()
                if (response.isSuccessful) {
                    val body = response.body()
                    _uiState.update {
                        it.copy(
                            characters = body?.characters ?: emptyList(),
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "毛仔现在连不上，等一下再试"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "毛仔现在连不上，等一下再试"
                    )
                }
            }
        }
    }

    fun selectCharacter(character: CharacterDto) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCharacterId = character.id, errorMessage = null) }
            try {
                val request = CreateConversationRequest(characterId = character.id)
                val response = api.createConversation(request)
                if (response.isSuccessful) {
                    val conversationId = response.body()?.id
                    if (conversationId != null) {
                        _uiState.update { it.copy(createdConversationId = conversationId) }
                    } else {
                        _uiState.update {
                            it.copy(selectedCharacterId = null, errorMessage = "出错了，再选一次吧")
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(selectedCharacterId = null, errorMessage = "毛仔现在连不上，等一下再试")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(selectedCharacterId = null, errorMessage = "毛仔现在连不上，等一下再试")
                }
            }
        }
    }

    fun clearConversationNavigation() {
        _uiState.update { it.copy(createdConversationId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
