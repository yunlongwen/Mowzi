package com.mowzi.app.audio

import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Audio player with queue management for sequential MP3 playback.
 * Handles TTS audio responses in order.
 */
class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val queue = ConcurrentLinkedQueue<String>() // MP3 base64
    private var isPlaying = false
    private var currentTempFile: File? = null

    fun enqueue(audioBase64: String) {
        queue.add(audioBase64)
        if (!isPlaying) {
            playNext()
        }
    }

    private fun playNext() {
        val audio = queue.poll() ?: run {
            isPlaying = false
            return
        }

        isPlaying = true
        try {
            val bytes = Base64.decode(audio, Base64.DEFAULT)
            val tempFile = File.createTempFile("tts_", ".mp3")
            currentTempFile = tempFile

            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    currentTempFile = null
                    playNext()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            isPlaying = false
            playNext() // Try next track
        }
    }

    fun stop() {
        isPlaying = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        queue.clear()
        currentTempFile?.delete()
        currentTempFile = null
    }

    fun isPlaying(): Boolean = isPlaying

    fun hasQueue(): Boolean = !queue.isEmpty() || isPlaying
}