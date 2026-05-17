package com.mowzi.app.ui.characters

import com.mowzi.app.MainDispatcherRule
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.CharacterDto
import com.mowzi.app.data.remote.dto.CharactersResponse
import com.mowzi.app.data.remote.dto.ConversationResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterSelectViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api: MowziApi = mock()
    private lateinit var viewModel: CharacterSelectViewModel

    private val testCharacters = listOf(
        CharacterDto("1", "猫头鹰医生", "https://example.com/owl.png", "知识渊博"),
        CharacterDto("2", "故事兔兔", "https://example.com/bunny.png", "温暖")
    )

    @Before
    fun setup() = runTest {
        whenever(api.getCharacters()).thenReturn(Response.success(CharactersResponse(emptyList())))
        viewModel = CharacterSelectViewModel(api)
        advanceUntilIdle()
    }

    @Test
    fun `loadCharacters success loads characters`() = runTest {
        whenever(api.getCharacters()).thenReturn(Response.success(CharactersResponse(testCharacters)))
        viewModel.loadCharacters()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.characters.size)
        assertEquals("猫头鹰医生", viewModel.uiState.value.characters[0].name)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `loadCharacters failure shows friendly message`() = runTest {
        whenever(api.getCharacters()).thenThrow(RuntimeException("network error"))
        viewModel.loadCharacters()
        advanceUntilIdle()
        assertEquals("毛仔现在连不上，等一下再试", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadCharacters server error shows friendly message`() = runTest {
        whenever(api.getCharacters()).thenReturn(Response.error(500, "".toResponseBody()))
        viewModel.loadCharacters()
        advanceUntilIdle()
        assertEquals("毛仔现在连不上，等一下再试", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `selectCharacter success creates conversation`() = runTest {
        val character = testCharacters[0]
        whenever(api.createConversation(any())).thenReturn(
            Response.success(ConversationResponse(id = "conv-1", characterId = "1", title = "新对话", status = "active", createdAt = 0L, updatedAt = 0L))
        )
        viewModel.selectCharacter(character)
        advanceUntilIdle()
        assertEquals("conv-1", viewModel.uiState.value.createdConversationId)
        assertEquals("1", viewModel.uiState.value.selectedCharacterId)
    }

    @Test
    fun `selectCharacter failure shows friendly message`() = runTest {
        val character = testCharacters[0]
        whenever(api.createConversation(any())).thenThrow(RuntimeException("network error"))
        viewModel.selectCharacter(character)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedCharacterId)
        assertEquals("毛仔现在连不上，等一下再试", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `selectCharacter null response body shows error`() = runTest {
        val character = testCharacters[0]
        whenever(api.createConversation(any())).thenReturn(
            Response.success(null)
        )
        viewModel.selectCharacter(character)
        advanceUntilIdle()
        assertEquals("出错了，再选一次吧", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clearError clears message`() = runTest {
        whenever(api.getCharacters()).thenThrow(RuntimeException("error"))
        viewModel.loadCharacters()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clearConversationNavigation resets conversationId`() = runTest {
        val character = testCharacters[0]
        whenever(api.createConversation(any())).thenReturn(
            Response.success(ConversationResponse(id = "conv-1", characterId = "1", title = "新对话", status = "active", createdAt = 0L, updatedAt = 0L))
        )
        viewModel.selectCharacter(character)
        advanceUntilIdle()
        assertEquals("conv-1", viewModel.uiState.value.createdConversationId)
        viewModel.clearConversationNavigation()
        assertNull(viewModel.uiState.value.createdConversationId)
    }
}
