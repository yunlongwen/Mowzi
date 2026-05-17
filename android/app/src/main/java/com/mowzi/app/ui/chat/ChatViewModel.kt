package com.mowzi.app.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowzi.app.audio.AudioPlayer
import com.mowzi.app.audio.AudioRecorder
import com.mowzi.app.data.local.entity.CachedMessageEntity
import com.mowzi.app.data.remote.dto.ChatStreamChunk
import com.mowzi.app.data.repository.ChatRepository
import com.mowzi.app.speech.XfyunSpeechService
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
    private val audioPlayer: AudioPlayer,
    private val speechService: XfyunSpeechService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "wyl"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var streamingJob: Job? = null
    private val sentenceBuffer = StringBuilder()
    private val sentenceEndingRegex = Regex("[。！？…\\.!?]")

    init {
        val conversationId: String? = savedStateHandle["conversationId"]
        Log.d(TAG, "ChatViewModel: init, conversationId=$conversationId")
        conversationId?.let {
            setConversation(it, "", "")
        }
    }

    fun setConversation(conversationId: String, characterId: String, characterName: String) {
        Log.d(TAG, "setConversation: conversationId=$conversationId, characterId=$characterId, characterName=$characterName")
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
        Log.d(TAG, "startRecording: called, current state=${_uiState.value.recordingState}")
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _uiState.update { it.copy(recordingState = RecordingState.Recording) }
            Log.d(TAG, "startRecording: state set to Recording")
            // 不再 collect，音频数据已在 AudioRecorder 内部累积
        }
    }

    fun stopRecordingAndSend() {
        Log.d(TAG, "stopRecordingAndSend: called")
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _uiState.update { it.copy(recordingState = RecordingState.Processing) }
            Log.d(TAG, "stopRecordingAndSend: state set to Processing")
            audioRecorder.stopRecording()
            val audioBytes = audioRecorder.getAccumulatedPcmData()
            Log.d(TAG, "stopRecordingAndSend: audioBytes size=${audioBytes.size}")

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

    private fun sendVoiceMessage(pcmData: ByteArray) {
        Log.d(TAG, "sendVoiceMessage: called, pcmData size=${pcmData.size}")
        viewModelScope.launch {
            try {
                Log.d(TAG, "sendVoiceMessage: calling speechService.recognizeFromPcm")
                val result = withContext(Dispatchers.IO) {
                    speechService.recognizeFromPcm(pcmData)
                }
                Log.d(TAG, "sendVoiceMessage: recognizeFromPcm result=${result.text}, confidence=${result.confidence}")

                if (result.text.isBlank() || result.confidence < 0.3f) {
                    _uiState.update {
                        it.copy(
                            recordingState = RecordingState.Idle,
                            errorMessage = "没听清哦，再说一次吧？"
                        )
                    }
                    return@launch
                }

                val userMessage = ChatMessageUi(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = result.text
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + userMessage,
                        recordingState = RecordingState.Idle
                    )
                }

                sendTextMessage(result.text)
            } catch (e: Exception) {
                Log.e(TAG, "STT失败", e)
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.Idle,
                        errorMessage = "没听清哦，再说一次吧？"
                    )
                }
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

            sentenceBuffer.clear()
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
                val content = chunk.content ?: ""
                val newContent = (_uiState.value.currentStreamingText ?: "") + content
                _uiState.update { it.copy(currentStreamingText = newContent) }
                updateMessageContent(messageId, newContent)
                handleTextForTts(content)
            }
            "text_done" -> {
                val newContent = chunk.content ?: _uiState.value.currentStreamingText
                _uiState.update { it.copy(currentStreamingText = newContent) }
                updateMessageContent(messageId, newContent)
                flushSentenceBuffer()
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

    private fun handleTextForTts(content: String) {
        sentenceBuffer.append(content)
        val match = sentenceEndingRegex.find(sentenceBuffer)
        if (match != null) {
            val endPos = match.range.last + 1
            val sentence = sentenceBuffer.substring(0, endPos).trim()
            sentenceBuffer.delete(0, endPos)
            if (sentence.isNotEmpty()) {
                synthesizeAndPlay(sentence)
            }
        }
    }

    private fun flushSentenceBuffer() {
        val remaining = sentenceBuffer.toString().trim()
        sentenceBuffer.clear()
        if (remaining.isNotEmpty()) {
            synthesizeAndPlay(remaining)
        }
    }

    private fun synthesizeAndPlay(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pcmData = speechService.synthesize(text)
                if (pcmData != null) {
                    audioPlayer.enqueuePcm(pcmData)
                }
            } catch (e: Exception) {
                Log.w(TAG, "TTS合成失败，跳过音频播放", e)
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
