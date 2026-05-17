package com.mowzi.app.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * PCM audio recorder with silence detection.
 * Records audio at 16kHz mono 16-bit PCM.
 */
class AudioRecorder(
    private val silenceThreshold: Double = 200.0,
    private val silenceTimeoutMs: Long = 3000L
) {
    private var recorder: android.media.AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var lastSoundTime = 0L

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
    }

    fun startRecording(): Flow<ShortArray> = callbackFlow {
        val bufferSize = android.media.AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )

        val audioRecord = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
            close()
            return@callbackFlow
        }

        recorder = audioRecord
        isRecording.set(true)
        audioRecord.startRecording()

        val buffer = ShortArray(bufferSize)

        try {
            while (isRecording.get()) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = buffer.copyOf(read)

                    // Silence detection using RMS
                    val rms = calculateRms(chunk, read)
                    if (rms > silenceThreshold) {
                        lastSoundTime = System.currentTimeMillis()
                    }

                    trySend(chunk)

                    // Auto-stop on silence timeout
                    if (lastSoundTime > 0 &&
                        System.currentTimeMillis() - lastSoundTime > silenceTimeoutMs) {
                        break
                    }
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
            recorder = null
            isRecording.set(false)
        }
        close()
    }

    private fun calculateRms(buffer: ShortArray, length: Int): Double {
        var sum = 0L
        for (i in 0 until length) {
            sum += buffer[i].toLong() * buffer[i].toLong()
        }
        return sqrt(sum.toDouble() / length)
    }

    fun stopRecording() {
        isRecording.set(false)
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}