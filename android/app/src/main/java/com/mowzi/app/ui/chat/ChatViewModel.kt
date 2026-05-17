package com.mowzi.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowzi.app.audio.AudioPlayer
import com.mowzi.app.audio.AudioRecorder
import com.mowzi.app.data.local.entity.CachedMessageEntity
import com.mowzi.app.data.remote.dto.ChatStreamChunk
import com.mowzi.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val recordingState: RecordingState = RecordingState.Idle,
    val streamingState: StreamingState = StreamingState.Idle,
    val currentStreamingText: String = "",
    val currentCharacterId: String = "",
    val currentCharacterName: String = "",
    val currentConversationId: String = "",
    val errorMessage: String? = null,
    val usageWarningMinutes: Int? = null,
    val usageLimitReached: Boolean = false
)

data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val audioLocalPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    object Processing : RecordingState()
}

sealed class StreamingState {
    object Idle : StreamingState()
    object Streaming : StreamingState()
    object Complete : StreamingState()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var streamingJob: Job? = null

    fun setConversation(conversationId: String, characterId: String, characterName: String) {
        _uiState.update {
            it.copy(
                currentConversationId = conversationId,
                currentCharacterId = characterId,
                currentCharacterName = characterName
            )
        }
        loadMessages(conversationId)
    }

    private fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            chatRepository.getMessages(conversationId).collect { entities ->
                val messages = entities.map { entity ->
                    ChatMessageUi(
                        id = entity.id,
                        role = entity.role,
                        content = entity.content,
                        audioLocalPath = entity.audioLocalPath,
                        timestamp = entity.timestamp
                    )
                }
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun startRecording() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _uiState.update { it.copy(recordingState = RecordingState.Recording) }
            audioRecorder.startRecording().collect { }
        }
    }

    fun stopRecordingAndSend() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _uiState.update { it.copy(recordingState = RecordingState.Processing) }
            audioRecorder.stopRecording()
            val audioBytes = audioRecorder.getAccumulatedPcmData()

            if (audioBytes.isEmpty()) {
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.Idle,
                        errorMessage = null
                    )
                }
                return@launch
            }

            sendVoiceMessage(audioBytes)
        }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        audioRecorder.stopRecording()
        _uiState.update { it.copy(recordingState = RecordingState.Idle) }
    }

    private suspend fun sendVoiceMessage(pcmData: ByteArray) {
        try {
            val text = withContext(Dispatchers.IO) {
                chatRepository.speechToText(pcmData)
            }

            if (text.isBlank()) {
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.Idle,
                        errorMessage = "没听清哦，再说一次吧？"
                    )
                }
                return
            }

            val userMessage = ChatMessageUi(
                id = UUID.randomUUID().toString(),
                role = "user",
                content = text
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    recordingState = RecordingState.Idle
                )
            }

            sendTextMessage(text)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    recordingState = RecordingState.Idle,
                    errorMessage = "没听清哦，再说一次吧？"
                )
            }
        }
    }

    fun sendTextMessage(text: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val conversationId = _uiState.value.currentConversationId
            val characterId = _uiState.value.currentCharacterId

            if (conversationId.isBlank() || characterId.isBlank()) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    streamingState = StreamingState.Streaming,
                    currentStreamingText = ""
                )
            }

            try {
                val streamingMessageId = UUID.randomUUID().toString()
                val streamingMessage = ChatMessageUi(
                    id = streamingMessageId,
                    role = "assistant",
                    content = ""
                )

                _uiState.update {
                    it.copy(
                        messages = it.messages + streamingMessage,
                        currentStreamingText = ""
                    )
                }

                chatRepository.streamChat(conversationId, characterId, text).collect { chunk ->
                    handleStreamChunk(chunk, streamingMessageId)
                }

                _uiState.update { it.copy(streamingState = StreamingState.Complete) }

            } catch (e: Exception) {
                val isInterrupted = _uiState.value.messages.lastOrNull()?.content?.isNotBlank() == true
                _uiState.update {
                    it.copy(
                        streamingState = StreamingState.Idle,
                        errorMessage = if (isInterrupted) "回答被打断了" else "毛仔在想呢，等一下再来找我吧"
                    )
                }
            }
        }
    }

    private fun handleStreamChunk(chunk: ChatStreamChunk, messageId: String) {
        when (chunk.type) {
            "text_chunk" -> {
                val newContent = (_uiState.value.currentStreamingText ?: "") + (chunk.content ?: "")
                _uiState.update { it.copy(currentStreamingText = newContent) }
                updateMessageContent(messageId, newContent)
            }
            "text_done" -> {
                val newContent = chunk.content ?: _uiState.value.currentStreamingText
                _uiState.update { it.copy(currentStreamingText = newContent) }
                updateMessageContent(messageId, newContent)
            }
            "sentence_audio" -> {
                chunk.audioData?.let { audioBase64 ->
                    try {
                        audioPlayer.enqueue(audioBase64)
                    } catch (_: Exception) {
                        // TTS合成失败 — 仅显示文字，不播放音频
                    }
                }
            }
            "done" -> {
                _uiState.update { it.copy(streamingState = StreamingState.Complete) }
            }
            "error" -> {
                val msg = chunk.content ?: ""
                val friendlyMsg = when {
                    msg.contains("USAGE_DAILY_LIMIT") -> "今天的时间用完啦，明天再来找毛仔吧！"
                    msg.contains("USAGE_SESSION_LIMIT") -> "今天的时间用完啦，明天再来找毛仔吧！"
                    msg.contains("BLOCKED_HOURS") -> "毛仔休息啦，明天再来找我吧"
                    msg.contains("CONTENT_BLOCKED") -> "我们来聊点别的吧"
                    msg.contains("XFYUN_QUOTA") -> "现在只能打字聊天哦"
                    else -> "毛仔在想呢，等一下再来找我吧"
                }

                val usageReached = msg.contains("USAGE_DAILY_LIMIT") || msg.contains("USAGE_SESSION_LIMIT")
                _uiState.update {
                    it.copy(
                        streamingState = StreamingState.Idle,
                        errorMessage = friendlyMsg,
                        usageLimitReached = usageReached
                    )
                }
            }
            "stream_end" -> {
                _uiState.update { it.copy(streamingState = StreamingState.Complete) }
            }
        }
    }

    fun handleUsageWarning(remainingMinutes: Int) {
        _uiState.update { it.copy(usageWarningMinutes = remainingMinutes) }
    }

    private fun updateMessageContent(messageId: String, content: String) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == messageId) msg.copy(content = content) else msg
            }
            state.copy(messages = updatedMessages)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissUsageWarning() {
        _uiState.update { it.copy(usageWarningMinutes = null) }
    }

    fun stopAudio() {
        audioPlayer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
    }
}
