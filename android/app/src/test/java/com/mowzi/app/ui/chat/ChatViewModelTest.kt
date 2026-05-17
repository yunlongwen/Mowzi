package com.mowzi.app.ui.chat

import com.mowzi.app.MainDispatcherRule
import com.mowzi.app.audio.AudioPlayer
import com.mowzi.app.audio.AudioRecorder
import com.mowzi.app.data.local.entity.CachedMessageEntity
import com.mowzi.app.data.remote.dto.ChatStreamChunk
import com.mowzi.app.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chatRepository: ChatRepository = mock()
    private val audioRecorder: AudioRecorder = mock()
    private val audioPlayer: AudioPlayer = mock()

    private val messagesFlow = MutableStateFlow<List<CachedMessageEntity>>(emptyList())

    @Before
    fun setup() {
        whenever(chatRepository.getMessages(any())).thenReturn(messagesFlow)
        whenever(audioRecorder.startRecording()).thenReturn(flow { })
        whenever(audioRecorder.getAccumulatedPcmData()).thenReturn(ByteArray(0))
    }

    private fun createViewModel(): ChatViewModel {
        return ChatViewModel(chatRepository, audioRecorder, audioPlayer)
    }

    @Test
    fun `initial state is empty`() {
        val vm = createViewModel()
        val state = vm.uiState.value
        assertTrue(state.messages.isEmpty())
        assertEquals(RecordingState.Idle, state.recordingState)
        assertEquals(StreamingState.Idle, state.streamingState)
        assertNull(state.errorMessage)
    }

    @Test
    fun `setConversation updates conversation info`() = runTest {
        val vm = createViewModel()
        vm.setConversation("conv1", "char1", "猫头鹰医生")
        assertEquals("conv1", vm.uiState.value.currentConversationId)
        assertEquals("char1", vm.uiState.value.currentCharacterId)
        assertEquals("猫头鹰医生", vm.uiState.value.currentCharacterName)
    }

    @Test
    fun `clearError clears error message`() {
        val vm = createViewModel()
        vm.clearError()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `dismissUsageWarning clears warning`() {
        val vm = createViewModel()
        vm.handleUsageWarning(5)
        assertEquals(5, vm.uiState.value.usageWarningMinutes)
        vm.dismissUsageWarning()
        assertNull(vm.uiState.value.usageWarningMinutes)
    }

    @Test
    fun `handleUsageWarning sets remaining minutes`() {
        val vm = createViewModel()
        vm.handleUsageWarning(3)
        assertEquals(3, vm.uiState.value.usageWarningMinutes)
    }

    @Test
    fun `cancelRecording resets state to Idle`() = runTest {
        val vm = createViewModel()
        vm.startRecording()
        advanceUntilIdle()
        assertEquals(RecordingState.Recording, vm.uiState.value.recordingState)
        vm.cancelRecording()
        assertEquals(RecordingState.Idle, vm.uiState.value.recordingState)
    }

    @Test
    fun `sendTextMessage does nothing without conversation`() = runTest {
        val vm = createViewModel()
        vm.sendTextMessage("hello")
        advanceUntilIdle()
        assertEquals(StreamingState.Idle, vm.uiState.value.streamingState)
    }

    @Test
    fun `error chunk with USAGE_DAILY_LIMIT shows friendly message`() = runTest {
        val vm = createViewModel()
        vm.setConversation("conv1", "char1", "毛仔")
        whenever(chatRepository.streamChat(any(), any(), any())).thenReturn(
            flow {
                emit(ChatStreamChunk(type = "error", content = "USAGE_DAILY_LIMIT"))
            }
        )
        vm.sendTextMessage("hi")
        advanceUntilIdle()
        assertEquals("今天的时间用完啦，明天再来找毛仔吧！", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.usageLimitReached)
    }

    @Test
    fun `error chunk with BLOCKED_HOURS shows rest message`() = runTest {
        val vm = createViewModel()
        vm.setConversation("conv1", "char1", "毛仔")
        whenever(chatRepository.streamChat(any(), any(), any())).thenReturn(
            flow {
                emit(ChatStreamChunk(type = "error", content = "BLOCKED_HOURS"))
            }
        )
        vm.sendTextMessage("hi")
        advanceUntilIdle()
        assertEquals("毛仔休息啦，明天再来找我吧", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.usageLimitReached)
    }

    @Test
    fun `error chunk with CONTENT_BLOCKED shows redirect message`() = runTest {
        val vm = createViewModel()
        vm.setConversation("conv1", "char1", "毛仔")
        whenever(chatRepository.streamChat(any(), any(), any())).thenReturn(
            flow {
                emit(ChatStreamChunk(type = "error", content = "CONTENT_BLOCKED"))
            }
        )
        vm.sendTextMessage("hi")
        advanceUntilIdle()
        assertEquals("我们来聊点别的吧", vm.uiState.value.errorMessage)
    }

    @Test
    fun `error chunk with XFYUN_QUOTA shows text-only message`() = runTest {
        val vm = createViewModel()
        vm.setConversation("conv1", "char1", "毛仔")
        whenever(chatRepository.streamChat(any(), any(), any())).thenReturn(
            flow {
                emit(ChatStreamChunk(type = "error", content = "XFYUN_QUOTA"))
            }
        )
        vm.sendTextMessage("hi")
        advanceUntilIdle()
        assertEquals("现在只能打字聊天哦", vm.uiState.value.errorMessage)
    }

    @Test
    fun `text_chunk accumulates streaming text`() = runTest {
        val vm = createViewModel()
        vm.setConversation("conv1", "char1", "毛仔")
        whenever(chatRepository.streamChat(any(), any(), any())).thenReturn(
            flow {
                emit(ChatStreamChunk(type = "text_chunk", content = "你好"))
                emit(ChatStreamChunk(type = "text_chunk", content = "呀"))
                emit(ChatStreamChunk(type = "done"))
            }
        )
        vm.sendTextMessage("hi")
        advanceUntilIdle()
        assertEquals("你好呀", vm.uiState.value.currentStreamingText)
        assertEquals(StreamingState.Complete, vm.uiState.value.streamingState)
    }

    @Test
    fun `stream exception shows thinking message when no text`() = runTest {
        val vm = createViewModel()
        vm.setConversation("conv1", "char1", "毛仔")
        whenever(chatRepository.streamChat(any(), any(), any())).thenReturn(
            flow { throw RuntimeException("connection lost") }
        )
        vm.sendTextMessage("hi")
        advanceUntilIdle()
        assertEquals("毛仔在想呢，等一下再来找我吧", vm.uiState.value.errorMessage)
    }

    @Test
    fun `stopAudio calls player stop`() {
        val vm = createViewModel()
        vm.stopAudio()
        verify(audioPlayer).stop()
    }

    @Test
    fun `stopRecordingAndSend with empty data resets to Idle`() = runTest {
        whenever(audioRecorder.getAccumulatedPcmData()).thenReturn(ByteArray(0))
        val vm = createViewModel()
        vm.stopRecordingAndSend()
        advanceUntilIdle()
        assertEquals(RecordingState.Idle, vm.uiState.value.recordingState)
    }
}
