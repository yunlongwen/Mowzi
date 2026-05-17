package com.mowzi.app.ui.conversations

import com.mowzi.app.MainDispatcherRule
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.ConversationListResponse
import com.mowzi.app.data.remote.dto.ConversationResponse
import com.mowzi.app.data.remote.dto.ResumeConversationResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api: MowziApi = mock()

    private val activeConversation = ConversationResponse(
        id = "1", characterId = "10", title = "Active Chat",
        status = "active", createdAt = 1000L, updatedAt = 2000L, lastMessageAt = 1500L
    )
    private val idleConversation = ConversationResponse(
        id = "2", characterId = "10", title = "Paused Chat",
        status = "idle", createdAt = 3000L, updatedAt = 4000L, lastMessageAt = 3500L
    )
    private val archivedConversation = ConversationResponse(
        id = "3", characterId = "10", title = "Archived Chat",
        status = "archived", createdAt = 5000L, updatedAt = 6000L, lastMessageAt = 5500L
    )

    private suspend fun setupDefaultMocks() {
        whenever(api.getConversations(status = "active")).thenReturn(
            Response.success(ConversationListResponse(listOf(activeConversation), 1, 1, 20, 1))
        )
        whenever(api.getConversations(status = "idle")).thenReturn(
            Response.success(ConversationListResponse(listOf(idleConversation), 1, 1, 20, 1))
        )
        whenever(api.getConversations(status = "archived")).thenReturn(
            Response.success(ConversationListResponse(listOf(archivedConversation), 1, 1, 20, 1))
        )
    }

    @Test
    fun `loadConversations success populates all lists`() = runTest {
        setupDefaultMocks()

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.activeConversations.size)
        assertEquals("1", state.activeConversations[0].id)
        assertEquals("Active Chat", state.activeConversations[0].title)

        assertEquals(1, state.pausedConversations.size)
        assertEquals("2", state.pausedConversations[0].id)

        assertEquals(1, state.archivedConversations.size)
        assertEquals("3", state.archivedConversations[0].id)
    }

    @Test
    fun `loadConversations failure sets error`() = runTest {
        whenever(api.getConversations(status = "active")).thenThrow(RuntimeException("network error"))

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("network error", state.error)
        assertTrue(state.activeConversations.isEmpty())
    }

    @Test
    fun `loadConversations with empty items uses empty lists`() = runTest {
        whenever(api.getConversations(status = "active")).thenReturn(
            Response.success(ConversationListResponse(emptyList(), 0, 1, 20, 0))
        )
        whenever(api.getConversations(status = "idle")).thenReturn(
            Response.success(ConversationListResponse(emptyList(), 0, 1, 20, 0))
        )
        whenever(api.getConversations(status = "archived")).thenReturn(
            Response.success(ConversationListResponse(emptyList(), 0, 1, 20, 0))
        )

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.activeConversations.isEmpty())
        assertTrue(state.pausedConversations.isEmpty())
        assertTrue(state.archivedConversations.isEmpty())
    }

    @Test
    fun `loadConversations unsuccessful response keeps empty list`() = runTest {
        whenever(api.getConversations(status = "active")).thenReturn(
            Response.success(null as ConversationListResponse?)
        )
        whenever(api.getConversations(status = "idle")).thenReturn(
            Response.success(null as ConversationListResponse?)
        )
        whenever(api.getConversations(status = "archived")).thenReturn(
            Response.success(null as ConversationListResponse?)
        )

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.activeConversations.isEmpty())
    }

    @Test
    fun `resumeConversation success reloads conversations`() = runTest {
        setupDefaultMocks()
        whenever(api.resumeConversation(1)).thenReturn(
            Response.success(
                ResumeConversationResponse(
                    id = 1, childId = 1, characterId = 10,
                    title = "Resumed", status = "active",
                    createdAt = 1000L, updatedAt = 2000L, lastMessageAt = 1500L
                )
            )
        )

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        vm.resumeConversation(1)
        advanceUntilIdle()

        // Should have called getConversations again (init + reload)
        verify(api, atLeast(2)).getConversations(status = "active")
    }

    @Test
    fun `resumeConversation failure sets error`() = runTest {
        setupDefaultMocks()
        whenever(api.resumeConversation(999)).thenThrow(RuntimeException("resume failed"))

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        vm.resumeConversation(999)
        advanceUntilIdle()

        assertEquals("resume failed", vm.uiState.value.error)
    }

    @Test
    fun `resumeConversation unsuccessful response does not reload`() = runTest {
        setupDefaultMocks()
        whenever(api.resumeConversation(1)).thenReturn(
            Response.error<ResumeConversationResponse>(404, okhttp3.ResponseBody.create(null, "not found"))
        )

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        vm.resumeConversation(1)
        advanceUntilIdle()

        // Should not have reloaded conversations beyond the initial load
        verify(api, times(1)).getConversations(status = "active")
    }

    @Test
    fun `initial state triggers loadConversations`() = runTest {
        setupDefaultMocks()

        val vm = ConversationListViewModel(api)
        advanceUntilIdle()

        verify(api).getConversations(status = "active")
        verify(api).getConversations(status = "idle")
        verify(api).getConversations(status = "archived")
    }
}
