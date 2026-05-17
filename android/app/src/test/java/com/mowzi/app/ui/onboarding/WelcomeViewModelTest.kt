package com.mowzi.app.ui.onboarding

import com.mowzi.app.MainDispatcherRule
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.ActiveConversationResponse
import com.mowzi.app.data.remote.dto.DeviceRegisterResponse
import com.mowzi.app.util.TokenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tokenManager: TokenManager = mock()
    private val api: MowziApi = mock()

    @Test
    fun `init without token shows welcome`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn(null)
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.hasToken)
    }

    @Test
    fun `init with token checks active conversation`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn("existing-token")
        whenever(api.getActiveConversation()).thenReturn(
            Response.success(
                ActiveConversationResponse(
                    id = "conv1", characterId = "char1", title = "测试",
                    status = "active", createdAt = 0L, updatedAt = 0L, lastMessageAt = 0L
                )
            )
        )
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.hasToken)
        assertEquals("conv1", viewModel.uiState.value.activeConversationId)
    }

    @Test
    fun `init with token but no active conversation`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn("existing-token")
        whenever(api.getActiveConversation()).thenReturn(Response.success(null))
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.hasToken)
        assertNull(viewModel.uiState.value.activeConversationId)
    }

    @Test
    fun `onChildNameChanged updates name`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn(null)
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        viewModel.onChildNameChanged("小明")
        assertEquals("小明", viewModel.uiState.value.childName)
    }

    @Test
    fun `registerDevice with blank name shows error`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn(null)
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        viewModel.registerDevice()
        advanceUntilIdle()
        assertEquals("请输入你的名字哦", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `registerDevice failure shows friendly error`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn(null)
        whenever(api.registerDevice(any())).thenThrow(RuntimeException("network error"))
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        viewModel.onChildNameChanged("小明")
        viewModel.registerDevice()
        advanceUntilIdle()
        assertEquals("毛仔现在连不上，等一下再试", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.registered)
    }

    @Test
    fun `registerDevice server error shows friendly error`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn(null)
        whenever(api.registerDevice(any())).thenReturn(Response.error(500, "".toResponseBody()))
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        viewModel.onChildNameChanged("小明")
        viewModel.registerDevice()
        advanceUntilIdle()
        assertEquals("毛仔现在连不上，等一下再试", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clearError resets error`() = runTest {
        whenever(tokenManager.getDeviceToken()).thenReturn(null)
        val viewModel = WelcomeViewModel(tokenManager, api)
        advanceUntilIdle()
        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
