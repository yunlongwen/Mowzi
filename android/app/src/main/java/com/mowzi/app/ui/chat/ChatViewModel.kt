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

/**
 * UI state for chat screen.
 */
data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val recordingState: RecordingState = RecordingState.Idle,
    val streamingState: StreamingState = StreamingState.Idle,
    val currentStreamingText: String = "",
    val currentCharacterId: String = "",
    val currentCharacterName: String = "",
    val currentConversationId: String = "",
    val errorMessage: String? = null
)

/**
 * Chat message UI model.
 */
data class ChatMessageUi(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val audioLocalPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Recording state enum.
 */
sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    object Processing : RecordingState()
}

/**
 * Streaming state enum.
 */
sealed class StreamingState {
    object Idle : StreamingState()
    object Streaming : StreamingState()
    object Complete : StreamingState()
}

/**
 * ViewModel for chat functionality including voice recording and streaming.
 */
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

    /**
     * Sets the current conversation and character.
     */
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

    /**
     * Starts voice recording.
     */
    fun startRecording() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _uiState.update { it.copy(recordingState = RecordingState.Recording) }
            audioRecorder.startRecording().collect { pcmChunk ->
                // PCM chunks are accumulated in AudioRecorder
            }
        }
    }

    /**
     * Stops recording and sends voice message.
     */
    fun stopRecordingAndSend() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _uiState.update { it.copy(recordingState = RecordingState.Processing) }

            audioRecorder.stopRecording()

            // Get accumulated audio data from recorder
            val audioBytes = audioRecorder.getAccumulatedPcmData()

            if (audioBytes.isEmpty()) {
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.Idle,
                        errorMessage = "No audio recorded"
                    )
                }
                return@launch
            }

            sendVoiceMessage(audioBytes)
        }
    }

    /**
     * Cancels current recording.
     */
    fun cancelRecording() {
        recordingJob?.cancel()
        audioRecorder.stopRecording()
        _uiState.update { it.copy(recordingState = RecordingState.Idle) }
    }

    /**
     * Sends a voice message (PCM data) through STT then streaming.
     */
    private suspend fun sendVoiceMessage(pcmData: ByteArray) {
        try {
            // Step 1: Speech to text
            val text = withContext(Dispatchers.IO) {
                chatRepository.speechToText(pcmData)
            }

            if (text.isBlank()) {
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.Idle,
                        errorMessage = "Could not understand audio"
                    )
                }
                return
            }

            // Step 2: Add user message
            val userMessageId = UUID.randomUUID().toString()
            val userMessage = ChatMessageUi(
                id = userMessageId,
                role = "user",
                content = text
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    recordingState = RecordingState.Idle
                )
            }

            // Step 3: Stream response
            sendTextMessage(text)

        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    recordingState = RecordingState.Idle,
                    errorMessage = "Failed to process voice: ${e.message}"
                )
            }
        }
    }

    /**
     * Sends a text message and streams the response.
     */
    fun sendTextMessage(text: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val conversationId = _uiState.value.currentConversationId
            val characterId = _uiState.value.currentCharacterId

            if (conversationId.isBlank() || characterId.isBlank()) {
                _uiState.update { it.copy(errorMessage = "No active conversation") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    streamingState = StreamingState.Streaming,
                    currentStreamingText = ""
                )
            }

            try {
                // Create placeholder for streaming response
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

                // Collect streaming chunks
                chatRepository.streamChat(conversationId, characterId, text).collect { chunk ->
                    handleStreamChunk(chunk, streamingMessageId)
                }

                _uiState.update { it.copy(streamingState = StreamingState.Complete) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        streamingState = StreamingState.Idle,
                        errorMessage = "Stream failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Handles a streaming chunk from the SSE response.
     */
    private fun handleStreamChunk(chunk: ChatStreamChunk, messageId: String) {
        when (chunk.type) {
            "text_chunk" -> {
                val newContent = (_uiState.value.currentStreamingText ?: "") + (chunk.content ?: "")
                _uiState.update {
                    it.copy(currentStreamingText = newContent)
                }
                // Update message content
                updateMessageContent(messageId, newContent)
            }
            "text_done" -> {
                // Text streaming complete, audio may still come
                val newContent = chunk.content ?: _uiState.value.currentStreamingText
                _uiState.update {
                    it.copy(currentStreamingText = newContent)
                }
                updateMessageContent(messageId, newContent)
            }
            "sentence_audio" -> {
                // Enqueue audio for playback
                chunk.audioData?.let { audioBase64 ->
                    audioPlayer.enqueue(audioBase64)
                }
            }
            "sentence_end" -> {
                // Sentence complete, could update UI
            }
            "done" -> {
                _uiState.update { it.copy(streamingState = StreamingState.Complete) }
            }
            "error" -> {
                _uiState.update {
                    it.copy(
                        streamingState = StreamingState.Idle,
                        errorMessage = chunk.content ?: "Stream error"
                    )
                }
            }
            "stream_end" -> {
                _uiState.update { it.copy(streamingState = StreamingState.Complete) }
            }
        }
    }

    private fun updateMessageContent(messageId: String, content: String) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == messageId) msg.copy(content = content) else msg
            }
            state.copy(messages = updatedMessages)
        }
    }

    /**
     * Clears any error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Stops audio playback.
     */
    fun stopAudio() {
        audioPlayer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
    }
}