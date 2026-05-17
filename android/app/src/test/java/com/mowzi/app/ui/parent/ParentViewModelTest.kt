package com.mowzi.app.ui.parent

import com.mowzi.app.MainDispatcherRule
import com.mowzi.app.data.remote.dto.ParentConversationDto
import com.mowzi.app.data.remote.dto.ParentSettingsResponse
import com.mowzi.app.data.remote.dto.ParentUsageItem
import com.mowzi.app.data.repository.ParentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class PinAuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val parentRepository: ParentRepository = mock()
    private lateinit var viewModel: PinAuthViewModel

    @Before
    fun setup() {
        viewModel = PinAuthViewModel(parentRepository)
    }

    @Test
    fun `initial state has empty pin`() {
        assertEquals("", viewModel.uiState.value.pin)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isAuthenticated)
    }

    @Test
    fun `onPinDigitEntered adds digit`() {
        viewModel.onPinDigitEntered("1")
        assertEquals("1", viewModel.uiState.value.pin)
        viewModel.onPinDigitEntered("2")
        assertEquals("12", viewModel.uiState.value.pin)
    }

    @Test
    fun `onPinDigitEntered limits to 4 digits`() {
        viewModel.onPinDigitEntered("1")
        viewModel.onPinDigitEntered("2")
        viewModel.onPinDigitEntered("3")
        viewModel.onPinDigitEntered("4")
        viewModel.onPinDigitEntered("5")
        assertEquals("1234", viewModel.uiState.value.pin)
    }

    @Test
    fun `onPinDigitDeleted removes last digit`() {
        viewModel.onPinDigitEntered("1")
        viewModel.onPinDigitEntered("2")
        viewModel.onPinDigitDeleted()
        assertEquals("1", viewModel.uiState.value.pin)
    }

    @Test
    fun `clearPin resets pin`() {
        viewModel.onPinDigitEntered("1")
        viewModel.onPinDigitEntered("2")
        viewModel.clearPin()
        assertEquals("", viewModel.uiState.value.pin)
    }

    @Test
    fun `submitPin with less than 4 digits shows error`() = runTest {
        viewModel.onPinDigitEntered("1")
        viewModel.onPinDigitEntered("2")
        viewModel.submitPin()
        advanceUntilIdle()
        assertEquals("请输入4位PIN码", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submitPin success authenticates`() = runTest {
        whenever(parentRepository.authenticate("1234")).thenReturn(Result.success("token123"))
        viewModel.onPinDigitEntered("1")
        viewModel.onPinDigitEntered("2")
        viewModel.onPinDigitEntered("3")
        viewModel.onPinDigitEntered("4")
        viewModel.submitPin()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submitPin failure shows friendly error`() = runTest {
        whenever(parentRepository.authenticate("1234")).thenReturn(Result.failure(Exception("wrong")))
        viewModel.onPinDigitEntered("1")
        viewModel.onPinDigitEntered("2")
        viewModel.onPinDigitEntered("3")
        viewModel.onPinDigitEntered("4")
        viewModel.submitPin()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertEquals("密码不对哦，再试试", viewModel.uiState.value.errorMessage)
        assertEquals("", viewModel.uiState.value.pin)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ParentDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val parentRepository: ParentRepository = mock()
    private lateinit var viewModel: ParentDashboardViewModel

    private val testSettings = ParentSettingsResponse(
        dailyLimitMin = 60,
        sessionLimitMin = 30,
        blockedHoursStart = "21:00",
        blockedHoursEnd = "07:00"
    )

    @Before
    fun setup() = runTest {
        whenever(parentRepository.getSettings()).thenReturn(Result.success(testSettings))
        viewModel = ParentDashboardViewModel(parentRepository)
        advanceUntilIdle()
    }

    @Test
    fun `init loads settings`() = runTest {
        advanceUntilIdle()
        assertEquals(60, viewModel.uiState.value.settings.dailyLimitMin)
        assertEquals(30, viewModel.uiState.value.settings.sessionLimitMin)
        assertEquals("21:00", viewModel.uiState.value.settings.blockedHoursStart)
    }

    @Test
    fun `updateDailyLimit updates state`() {
        viewModel.updateDailyLimit(45)
        assertEquals(45, viewModel.uiState.value.settings.dailyLimitMin)
    }

    @Test
    fun `updateSessionLimit updates state`() {
        viewModel.updateSessionLimit(20)
        assertEquals(20, viewModel.uiState.value.settings.sessionLimitMin)
    }

    @Test
    fun `updateBlockedHoursStart updates state`() {
        viewModel.updateBlockedHoursStart("22:00")
        assertEquals("22:00", viewModel.uiState.value.settings.blockedHoursStart)
    }

    @Test
    fun `updateBlockedHoursEnd updates state`() {
        viewModel.updateBlockedHoursEnd("08:00")
        assertEquals("08:00", viewModel.uiState.value.settings.blockedHoursEnd)
    }

    @Test
    fun `saveSettings success shows saveSuccess`() = runTest {
        whenever(parentRepository.updateSettings(any())).thenReturn(Result.success(testSettings))
        viewModel.saveSettings()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.saveSuccess)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `saveSettings failure shows error`() = runTest {
        whenever(parentRepository.updateSettings(any())).thenReturn(Result.failure(Exception("fail")))
        viewModel.saveSettings()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.saveSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `loadUsage daily updates dailyUsage`() = runTest {
        val usageItems = listOf(ParentUsageItem(date = "2026-01-01", minutes = 30, messageCount = 10))
        whenever(parentRepository.getUsage("daily")).thenReturn(Result.success(usageItems))
        viewModel.loadUsage("daily")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.dailyUsage.size)
        assertEquals(30, viewModel.uiState.value.dailyUsage[0].minutes)
    }

    @Test
    fun `loadConversations updates conversations list`() = runTest {
        val conversations = listOf(
            ParentConversationDto("conv1", "猫头鹰医生", "测试对话", 5, System.currentTimeMillis())
        )
        whenever(parentRepository.getConversations()).thenReturn(Result.success(conversations))
        viewModel.loadConversations()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.conversations.size)
        assertEquals("测试对话", viewModel.uiState.value.conversations[0].title)
    }

    @Test
    fun `clearSaveSuccess resets flag`() = runTest {
        whenever(parentRepository.updateSettings(any())).thenReturn(Result.success(testSettings))
        viewModel.saveSettings()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.saveSuccess)
        viewModel.clearSaveSuccess()
        assertFalse(viewModel.uiState.value.saveSuccess)
    }

    @Test
    fun `clearError resets error`() {
        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
