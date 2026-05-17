package com.mowzi.app.ui.onboarding

import android.annotation.SuppressLint
import android.provider.Settings
import android.util.Log
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
import okhttp3.ResponseBody
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
        private const val TAG = "wyl"
        val BASE_URL_KEY = stringPreferencesKey("api_base_url")
    }

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        loadServerUrl()
        Log.d(TAG, "init: checking existing token...")
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
            Log.d(TAG, "checkExistingToken: token=${token?.take(20)}...")
            if (token != null) {
                _uiState.update { it.copy(hasToken = true) }
                checkActiveConversation()
            } else {
                Log.d(TAG, "checkExistingToken: no token found, showing registration")
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
            Log.d(TAG, "registerDevice: starting registration for name=$name")

            try {
                val deviceId = Settings.Secure.ANDROID_ID ?: "unknown"
                Log.d(TAG, "registerDevice: deviceId=$deviceId")
                val response = api.registerDevice(
                    DeviceRegisterRequest(
                        deviceId = deviceId,
                        deviceName = name,
                        deviceModel = android.os.Build.MODEL
                    )
                )

                Log.d(TAG, "registerDevice: response.isSuccessful=${response.isSuccessful}, code=${response.code()}")
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.deviceToken != null) {
                        Log.d(TAG, "registerDevice: success, token=${body.deviceToken.take(20)}...")
                        tokenManager.saveDeviceToken(body.deviceToken)
                        _uiState.update { it.copy(isRegistering = false, registered = true) }
                    } else {
                        Log.d(TAG, "registerDevice: failed - body.success=${body?.success}, token=${body?.deviceToken}")
                        _uiState.update {
                            it.copy(isRegistering = false, errorMessage = "注册失败，再试一次吧")
                        }
                    }
                } else {
                    Log.d(TAG, "registerDevice: HTTP failed with code ${response.code()}")
                    _uiState.update {
                        it.copy(isRegistering = false, errorMessage = "毛仔现在连不上，等一下再试")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerDevice: exception", e)
                _uiState.update {
                    it.copy(isRegistering = false, errorMessage = "毛仔现在连不上，等一下再试")
                }
            }
        }
    }

    private fun checkActiveConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingActive = true) }
            Log.d(TAG, "checkActiveConversation: starting...")
            try {
                val response = api.getActiveConversationRaw()
                Log.d(TAG, "checkActiveConversation: response.isSuccessful=${response.isSuccessful}, code=${response.code()}")
                if (response.isSuccessful) {
                    val body = response.body()
                    val bodyString = body?.string() ?: ""
                    Log.d(TAG, "checkActiveConversation: raw body='$bodyString'")
                    val trimmed = bodyString.trim()
                    if (trimmed.isNotEmpty() && trimmed != "null" && trimmed != "[]" && trimmed.startsWith("{")) {
                        val id = Regex("\"id\"\\s*:\\s*\"?(\\d+)").find(bodyString)?.groupValues?.get(1)
                        if (id != null) {
                            Log.d(TAG, "checkActiveConversation: found conversation id=$id")
                            _uiState.update {
                                it.copy(isCheckingActive = false, activeConversationId = id)
                            }
                        } else {
                            Log.d(TAG, "checkActiveConversation: no conversation found in body")
                            _uiState.update { it.copy(isCheckingActive = false) }
                        }
                    } else {
                        Log.d(TAG, "checkActiveConversation: no active conversation (null or empty)")
                        _uiState.update { it.copy(isCheckingActive = false) }
                    }
                } else {
                    Log.d(TAG, "checkActiveConversation: failed with code ${response.code()}")
                    _uiState.update { it.copy(isCheckingActive = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkActiveConversation: exception", e)
                _uiState.update { it.copy(isCheckingActive = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
