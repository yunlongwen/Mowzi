package com.mowzi.app.data.repository

import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.*
import com.mowzi.app.util.TokenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ParentRepositoryImplTest {

    private val api: MowziApi = mock()
    private val tokenManager: TokenManager = mock()
    private lateinit var repository: ParentRepositoryImpl

    @Before
    fun setup() {
        repository = ParentRepositoryImpl(api, tokenManager)
    }

    // --- authenticate ---

    @Test
    fun `authenticate success saves token and returns it`() = runTest {
        val authResponse = AuthTokenResponse(success = true, token = "parent-token-123")
        whenever(api.parentAuth(any())).thenReturn(Response.success(authResponse))

        val result = repository.authenticate("1234")

        assertTrue(result.isSuccess)
        assertEquals("parent-token-123", result.getOrNull())
        verify(tokenManager).saveParentToken("parent-token-123")
    }

    @Test
    fun `authenticate failure with wrong PIN returns error`() = runTest {
        whenever(api.parentAuth(any())).thenReturn(
            Response.error(401, okhttp3.ResponseBody.create(null, "unauthorized"))
        )

        val result = repository.authenticate("0000")

        assertTrue(result.isFailure)
        assertEquals("PIN码错误", result.exceptionOrNull()?.message)
    }

    @Test
    fun `authenticate with unsuccessful body returns error`() = runTest {
        val authResponse = AuthTokenResponse(success = false, token = null)
        whenever(api.parentAuth(any())).thenReturn(Response.success(authResponse))

        val result = repository.authenticate("1234")

        assertTrue(result.isFailure)
        assertEquals("Authentication failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `authenticate with null token returns error`() = runTest {
        val authResponse = AuthTokenResponse(success = true, token = null)
        whenever(api.parentAuth(any())).thenReturn(Response.success(authResponse))

        val result = repository.authenticate("1234")

        assertTrue(result.isFailure)
    }

    @Test
    fun `authenticate exception returns failure`() = runTest {
        whenever(api.parentAuth(any())).thenThrow(RuntimeException("network error"))

        val result = repository.authenticate("1234")

        assertTrue(result.isFailure)
        assertEquals("network error", result.exceptionOrNull()?.message)
    }

    // --- getSettings ---

    @Test
    fun `getSettings success returns settings`() = runTest {
        val settings = ParentSettingsResponse(
            dailyLimitMin = 60,
            sessionLimitMin = 30,
            blockedHoursStart = "21:00",
            blockedHoursEnd = "07:00"
        )
        whenever(api.getParentSettings()).thenReturn(Response.success(settings))

        val result = repository.getSettings()

        assertTrue(result.isSuccess)
        assertEquals(60, result.getOrNull()?.dailyLimitMin)
        assertEquals("21:00", result.getOrNull()?.blockedHoursStart)
    }

    @Test
    fun `getSettings failure returns error`() = runTest {
        whenever(api.getParentSettings()).thenReturn(
            Response.error(500, okhttp3.ResponseBody.create(null, "server error"))
        )

        val result = repository.getSettings()

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }

    @Test
    fun `getSettings with null body returns failure`() = runTest {
        whenever(api.getParentSettings()).thenReturn(
            Response.success(null as ParentSettingsResponse?)
        )

        val result = repository.getSettings()

        assertTrue(result.isFailure)
    }

    @Test
    fun `getSettings exception returns failure`() = runTest {
        whenever(api.getParentSettings()).thenThrow(RuntimeException("timeout"))

        val result = repository.getSettings()

        assertTrue(result.isFailure)
        assertEquals("timeout", result.exceptionOrNull()?.message)
    }

    // --- updateSettings ---

    @Test
    fun `updateSettings success returns updated settings`() = runTest {
        val updatedSettings = ParentSettingsResponse(dailyLimitMin = 45, sessionLimitMin = 20)
        whenever(api.updateParentSettings(any())).thenReturn(Response.success(updatedSettings))

        val result = repository.updateSettings(ParentSettingsRequest(dailyLimitMin = 45))

        assertTrue(result.isSuccess)
        assertEquals(45, result.getOrNull()?.dailyLimitMin)
    }

    @Test
    fun `updateSettings failure returns error`() = runTest {
        whenever(api.updateParentSettings(any())).thenReturn(
            Response.error(400, okhttp3.ResponseBody.create(null, "bad request"))
        )

        val result = repository.updateSettings(ParentSettingsRequest())

        assertTrue(result.isFailure)
    }

    @Test
    fun `updateSettings with null body returns failure`() = runTest {
        whenever(api.updateParentSettings(any())).thenReturn(
            Response.success(null as ParentSettingsResponse?)
        )

        val result = repository.updateSettings(ParentSettingsRequest())

        assertTrue(result.isFailure)
    }

    @Test
    fun `updateSettings exception returns failure`() = runTest {
        whenever(api.updateParentSettings(any())).thenThrow(RuntimeException("network error"))

        val result = repository.updateSettings(ParentSettingsRequest())

        assertTrue(result.isFailure)
    }

    // --- getUsage ---

    @Test
    fun `getUsage success returns usage items`() = runTest {
        val usageItems = listOf(
            ParentUsageItem(date = "2026-01-01", minutes = 30, messageCount = 10)
        )
        whenever(api.getParentUsage(any())).thenReturn(Response.success(ParentUsageResponse(usageItems)))

        val result = repository.getUsage("daily")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals(30, result.getOrNull()?.get(0)?.minutes)
    }

    @Test
    fun `getUsage with null body returns empty list`() = runTest {
        whenever(api.getParentUsage(any())).thenReturn(
            Response.success(null as ParentUsageResponse?)
        )

        val result = repository.getUsage("daily")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `getUsage failure returns error`() = runTest {
        whenever(api.getParentUsage(any())).thenReturn(
            Response.error(500, okhttp3.ResponseBody.create(null, "error"))
        )

        val result = repository.getUsage("daily")

        assertTrue(result.isFailure)
    }

    // --- getConversations ---

    @Test
    fun `getConversations success returns conversations`() = runTest {
        val conversations = listOf(
            ParentConversationDto("conv1", "猫头鹰医生", "Test", 5, 1000L)
        )
        whenever(api.getParentConversations()).thenReturn(
            Response.success(ParentConversationsResponse(conversations))
        )

        val result = repository.getConversations()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("conv1", result.getOrNull()?.get(0)?.id)
    }

    @Test
    fun `getConversations with null body returns empty list`() = runTest {
        whenever(api.getParentConversations()).thenReturn(
            Response.success(null as ParentConversationsResponse?)
        )

        val result = repository.getConversations()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `getConversations failure returns error`() = runTest {
        whenever(api.getParentConversations()).thenReturn(
            Response.error(500, okhttp3.ResponseBody.create(null, "error"))
        )

        val result = repository.getConversations()

        assertTrue(result.isFailure)
    }

    // --- getMessages ---

    @Test
    fun `getMessages success returns messages`() = runTest {
        val messages = listOf(
            ParentMessageDto(role = "user", content = "Hi", timestamp = 1000L)
        )
        whenever(api.getParentMessages("conv1")).thenReturn(
            Response.success(ParentMessagesResponse(messages))
        )

        val result = repository.getMessages("conv1")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Hi", result.getOrNull()?.get(0)?.content)
    }

    @Test
    fun `getMessages with null body returns empty list`() = runTest {
        whenever(api.getParentMessages("conv1")).thenReturn(
            Response.success(null as ParentMessagesResponse?)
        )

        val result = repository.getMessages("conv1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `getMessages failure returns error`() = runTest {
        whenever(api.getParentMessages("conv1")).thenReturn(
            Response.error(500, okhttp3.ResponseBody.create(null, "error"))
        )

        val result = repository.getMessages("conv1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getMessages exception returns failure`() = runTest {
        whenever(api.getParentMessages("conv1")).thenThrow(RuntimeException("timeout"))

        val result = repository.getMessages("conv1")

        assertTrue(result.isFailure)
        assertEquals("timeout", result.exceptionOrNull()?.message)
    }
}
