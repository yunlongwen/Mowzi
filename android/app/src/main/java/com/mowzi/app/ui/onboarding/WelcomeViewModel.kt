package com.mowzi.app.ui.onboarding

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowzi.app.AppConfig
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.DeviceRegisterRequest
import com.mowzi.app.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    val hasToken: Boolean? = null,
    val childName: String = "",
    val isRegistering: Boolean = false,
    val isCheckingActive: Boolean = false,
    val activeConversationId: String? = null,
    val errorMessage: String? = null,
    val registered: Boolean = false,
    val serverUrl: String = AppConfig.DEFAULT_API_URL
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val api: MowziApi,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val BASE_URL_KEY = stringPreferencesKey("api_base_url")
    }

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        loadServerUrl()
        checkExistingToken()
    }

    private fun loadServerUrl() {
        viewModelScope.launch {
            val url = dataStore.data.first()[BASE_URL_KEY] ?: AppConfig.DEFAULT_API_URL
            _uiState.update { it.copy(serverUrl = url) }
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[BASE_URL_KEY] = url
            }
            _uiState.update { it.copy(serverUrl = url) }
        }
    }

    private fun checkExistingToken() {
        viewModelScope.launch {
            val token = tokenManager.getDeviceToken()
            if (token != null) {
                _uiState.update { it.copy(hasToken = true) }
                checkActiveConversation()
            } else {
                _uiState.update { it.copy(hasToken = false) }
            }
        }
    }

    fun onChildNameChanged(name: String) {
        _uiState.update { it.copy(childName = name, errorMessage = null) }
    }

    @SuppressLint("HardwareIds")
    fun registerDevice() {
        val name = _uiState.value.childName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入你的名字哦") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, errorMessage = null) }

            try {
                val deviceId = Settings.Secure.ANDROID_ID ?: "unknown"
                val response = api.registerDevice(
                    DeviceRegisterRequest(
                        deviceId = deviceId,
                        deviceName = name,
                        deviceModel = android.os.Build.MODEL
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.deviceToken != null) {
                        tokenManager.saveDeviceToken(body.deviceToken)
                        _uiState.update { it.copy(isRegistering = false, registered = true) }
                    } else {
                        _uiState.update {
                            it.copy(isRegistering = false, errorMessage = "注册失败，再试一次吧")
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(isRegistering = false, errorMessage = "毛仔现在连不上，等一下再试")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRegistering = false, errorMessage = "毛仔现在连不上，等一下再试")
                }
            }
        }
    }

    private fun checkActiveConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingActive = true) }
            try {
                val response = api.getActiveConversation()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        _uiState.update {
                            it.copy(isCheckingActive = false, activeConversationId = body.id)
                        }
                    } else {
                        _uiState.update { it.copy(isCheckingActive = false) }
                    }
                } else {
                    _uiState.update { it.copy(isCheckingActive = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCheckingActive = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
