package com.mowzi.app.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowzi.app.data.remote.dto.ParentConversationDto
import com.mowzi.app.data.remote.dto.ParentMessageDto
import com.mowzi.app.data.remote.dto.ParentSettingsRequest
import com.mowzi.app.data.remote.dto.ParentSettingsResponse
import com.mowzi.app.data.remote.dto.ParentUsageItem
import com.mowzi.app.data.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for PIN entry screen.
 */
data class PinEntryUiState(
    val pin: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
)

/**
 * UI state for parent dashboard screen.
 */
data class ParentDashboardUiState(
    val settings: ParentSettingsResponse = ParentSettingsResponse(),
    val dailyUsage: List<ParentUsageItem> = emptyList(),
    val weeklyUsage: List<ParentUsageItem> = emptyList(),
    val conversations: List<ParentConversationDto> = emptyList(),
    val selectedConversation: ParentConversationDto? = null,
    val selectedConversationMessages: List<ParentMessageDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
)

/**
 * ViewModel for PIN authentication.
 */
@HiltViewModel
class PinAuthViewModel @Inject constructor(
    private val parentRepository: ParentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinEntryUiState())
    val uiState: StateFlow<PinEntryUiState> = _uiState.asStateFlow()

    fun onPinDigitEntered(digit: String) {
        if (_uiState.value.pin.length < 4) {
            _uiState.update { it.copy(pin = it.pin + digit, errorMessage = null) }
        }
    }

    fun onPinDigitDeleted() {
        _uiState.update { it.copy(pin = it.pin.dropLast(1), errorMessage = null) }
    }

    fun clearPin() {
        _uiState.update { it.copy(pin = "", errorMessage = null) }
    }

    fun submitPin() {
        val pin = _uiState.value.pin
        if (pin.length != 4) {
            _uiState.update { it.copy(errorMessage = "请输入4位PIN码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            parentRepository.authenticate(pin).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "密码不对哦，再试试",
                            pin = ""
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * ViewModel for parent dashboard.
 */
@HiltViewModel
class ParentDashboardViewModel @Inject constructor(
    private val parentRepository: ParentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentDashboardUiState())
    val uiState: StateFlow<ParentDashboardUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            parentRepository.getSettings().fold(
                onSuccess = { settings ->
                    _uiState.update { it.copy(settings = settings, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "无法加载设置: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun loadUsage(period: String) {
        viewModelScope.launch {
            parentRepository.getUsage(period).fold(
                onSuccess = { usageItems ->
                    _uiState.update {
                        if (period == "daily") {
                            it.copy(dailyUsage = usageItems)
                        } else {
                            it.copy(weeklyUsage = usageItems)
                        }
                    }
                },
                onFailure = { /* Silently fail for usage stats */ }
            )
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            parentRepository.getConversations().fold(
                onSuccess = { conversations ->
                    _uiState.update { it.copy(conversations = conversations) }
                },
                onFailure = { /* Silently fail */ }
            )
        }
    }

    fun selectConversation(conversation: ParentConversationDto) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedConversation = conversation) }

            parentRepository.getMessages(conversation.id).fold(
                onSuccess = { messages ->
                    _uiState.update { it.copy(selectedConversationMessages = messages) }
                },
                onFailure = { /* Silently fail */ }
            )
        }
    }

    fun clearSelectedConversation() {
        _uiState.update {
            it.copy(
                selectedConversation = null,
                selectedConversationMessages = emptyList()
            )
        }
    }

    fun updateDailyLimit(minutes: Int) {
        _uiState.update {
            it.copy(settings = it.settings.copy(dailyLimitMin = minutes))
        }
    }

    fun updateSessionLimit(minutes: Int) {
        _uiState.update {
            it.copy(settings = it.settings.copy(sessionLimitMin = minutes))
        }
    }

    fun updateBlockedHoursStart(time: String?) {
        _uiState.update {
            it.copy(settings = it.settings.copy(blockedHoursStart = time))
        }
    }

    fun updateBlockedHoursEnd(time: String?) {
        _uiState.update {
            it.copy(settings = it.settings.copy(blockedHoursEnd = time))
        }
    }

    fun updateLlMUrl(url: String?) {
        _uiState.update {
            it.copy(settings = it.settings.copy(llmApiUrl = url))
        }
    }

    fun updateLlMModel(model: String?) {
        _uiState.update {
            it.copy(settings = it.settings.copy(llmModel = model))
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = false, errorMessage = null) }

            val currentSettings = _uiState.value.settings
            val request = ParentSettingsRequest(
                dailyLimitMin = currentSettings.dailyLimitMin,
                sessionLimitMin = currentSettings.sessionLimitMin,
                blockedHoursStart = currentSettings.blockedHoursStart,
                blockedHoursEnd = currentSettings.blockedHoursEnd,
                llmApiUrl = currentSettings.llmApiUrl,
                llmModel = currentSettings.llmModel,
                xfyunAppId = currentSettings.xfyunAppId
            )

            parentRepository.updateSettings(request).fold(
                onSuccess = { updatedSettings ->
                    _uiState.update {
                        it.copy(
                            settings = updatedSettings,
                            isSaving = false,
                            saveSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "保存失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}