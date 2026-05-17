package com.mowzi.app.ui.characters

import android.util.Log
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

    companion object {
        private const val TAG = "wyl"
    }

    private val _uiState = MutableStateFlow(CharacterSelectUiState())
    val uiState: StateFlow<CharacterSelectUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "CharacterSelectViewModel: init")
        loadCharacters()
    }

    fun loadCharacters() {
        Log.d(TAG, "CharacterSelectViewModel: loadCharacters started")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = api.getCharacters()
                Log.d(TAG, "CharacterSelectViewModel: getCharacters response.isSuccessful=${response.isSuccessful}, code=${response.code()}, body=${response.body()}")
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "CharacterSelectViewModel: body chars count=${body?.characters?.size}")
                    _uiState.update {
                        it.copy(
                            characters = body?.characters ?: emptyList(),
                            isLoading = false
                        )
                    }
                } else {
                    Log.d(TAG, "CharacterSelectViewModel: getCharacters failed, setting error")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "毛仔现在连不上，等一下再试"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CharacterSelectViewModel: loadCharacters exception", e)
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
        Log.d(TAG, "CharacterSelectViewModel: selectCharacter called, characterId=${character.id}")
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCharacterId = character.id, errorMessage = null) }
            try {
                val request = CreateConversationRequest(characterId = character.id)
                Log.d(TAG, "CharacterSelectViewModel: createConversation request sent")
                val response = api.createConversation(request)
                Log.d(TAG, "CharacterSelectViewModel: createConversation response.isSuccessful=${response.isSuccessful}, code=${response.code()}")
                if (response.isSuccessful) {
                    val conversationId = response.body()?.id
                    Log.d(TAG, "CharacterSelectViewModel: conversationId=$conversationId")
                    if (conversationId != null) {
                        _uiState.update { it.copy(createdConversationId = conversationId) }
                    } else {
                        Log.d(TAG, "CharacterSelectViewModel: conversationId is null, setting error")
                        _uiState.update {
                            it.copy(selectedCharacterId = null, errorMessage = "出错了，再选一次吧")
                        }
                    }
                } else {
                    Log.d(TAG, "CharacterSelectViewModel: createConversation failed with code ${response.code()}, setting error")
                    _uiState.update {
                        it.copy(selectedCharacterId = null, errorMessage = "毛仔现在连不上，等一下再试")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CharacterSelectViewModel: selectCharacter exception", e)
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
