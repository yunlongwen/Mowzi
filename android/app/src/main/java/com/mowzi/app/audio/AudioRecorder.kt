package com.mowzi.app.audio

import android.util.Log
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * PCM audio recorder with silence detection.
 * Records audio at 16kHz mono 16-bit PCM.
 */
class AudioRecorder(
    private val silenceThreshold: Double = 200.0,
    private val silenceTimeoutMs: Long = 3000L,
    private val maxDurationMs: Long = 60000L // 60 seconds max
) {
    companion object {
        private const val TAG = "wyl"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
    }

    private var recorder: android.media.AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var lastSoundTime = 0L
    private var recordingStartTime = 0L

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    // Accumulated PCM data (as bytes for STT)
    private val accumulatedPcmData = mutableListOf<ByteArray>()
    private val lock = Any()

    fun startRecording() {
        Log.d(TAG, "AudioRecorder.startRecording: START")

        handlerThread = HandlerThread("AudioRecorder").apply { start() }
        handler = Handler(handlerThread!!.looper)

        val bufferSize = android.media.AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        Log.d(TAG, "AudioRecorder: bufferSize=$bufferSize")

        val audioRecord = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        Log.d(TAG, "AudioRecorder: AudioRecord created, state=${audioRecord.state}")

        if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecorder: AudioRecord NOT initialized, state=${audioRecord.state}")
            handlerThread?.quit()
            isRecording.set(false)
            return
        }

        recorder = audioRecord
        isRecording.set(true)
        recordingStartTime = System.currentTimeMillis()
        lastSoundTime = 0L
        synchronized(lock) {
            accumulatedPcmData.clear()
        }
        audioRecord.startRecording()
        Log.d(TAG, "AudioRecorder: recording started, isRecording=${isRecording.get()}")

        val buffer = ShortArray(bufferSize)

        handler?.post {
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

                        // Accumulate PCM data as bytes
                        val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until read) {
                            byteBuffer.putShort(chunk[i])
                        }
                        synchronized(lock) {
                            accumulatedPcmData.add(byteBuffer.array())
                        }

                        // Auto-stop on silence timeout
                        if (lastSoundTime > 0 &&
                            System.currentTimeMillis() - lastSoundTime > silenceTimeoutMs) {
                            Log.d(TAG, "AudioRecorder: silence timeout")
                            break
                        }

                        // Hard stop on max duration
                        if (System.currentTimeMillis() - recordingStartTime > maxDurationMs) {
                            Log.d(TAG, "AudioRecorder: max duration")
                            break
                        }
                    }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
                recorder = null
                isRecording.set(false)
                handlerThread?.quit()
            }
        }
    }

    /**
     * Returns accumulated PCM audio data.
     */
    fun getAccumulatedPcmData(): ByteArray {
        synchronized(lock) {
            return accumulatedPcmData.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        }
    }

    private fun calculateRms(buffer: ShortArray, length: Int): Double {
        var sum = 0L
        for (i in 0 until length) {
            sum += buffer[i].toLong() * buffer[i].toLong()
        }
        return sqrt(sum.toDouble() / length)
    }

    fun stopRecording() {
        Log.d(TAG, "AudioRecorder.stopRecording: called, isRecording=${isRecording.get()}")
        isRecording.set(false)
    }
}