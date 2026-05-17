package com.mowzi.app.audio

import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val base64Queue = ConcurrentLinkedQueue<String>()
    private val pcmQueue = ConcurrentLinkedQueue<File>()
    private var isPlaying = false
    private var currentTempFile: File? = null

    fun enqueue(audioBase64: String) {
        base64Queue.add(audioBase64)
        if (!isPlaying) playNext()
    }

    fun enqueuePcm(pcmData: ByteArray) {
        val wavData = pcmToWav(pcmData, 16000, 16, 1)
        val tempFile = File.createTempFile("tts_", ".wav")
        tempFile.writeBytes(wavData)
        pcmQueue.add(tempFile)
        if (!isPlaying) playNext()
    }

    private fun playNext() {
        // Priority: base64 queue first, then PCM queue
        val audio = base64Queue.poll()
        if (audio != null) {
            playBase64(audio)
            return
        }

        val wavFile = pcmQueue.poll()
        if (wavFile != null) {
            playWavFile(wavFile)
            return
        }

        isPlaying = false
    }

    private fun playBase64(audioBase64: String) {
        isPlaying = true
        try {
            val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val tempFile = File.createTempFile("tts_", ".mp3")
            currentTempFile = tempFile
            FileOutputStream(tempFile).use { it.write(bytes) }
            playFile(tempFile)
        } catch (e: Exception) {
            isPlaying = false
            playNext()
        }
    }

    private fun playWavFile(wavFile: File) {
        isPlaying = true
        playFile(wavFile)
    }

    private fun playFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    file.delete()
                    if (currentTempFile == file) currentTempFile = null
                    playNext()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            file.delete()
            if (currentTempFile == file) currentTempFile = null
            isPlaying = false
            playNext()
        }
    }

    fun stop() {
        isPlaying = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        base64Queue.clear()
        pcmQueue.forEach { it.delete() }
        pcmQueue.clear()
        currentTempFile?.delete()
        currentTempFile = null
    }

    fun isPlaying(): Boolean = isPlaying

    fun hasQueue(): Boolean = !base64Queue.isEmpty() || !pcmQueue.isEmpty() || isPlaying

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, bitsPerSample: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, 36 + dataSize)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1) // PCM format
        writeShortLE(header, 22, channels.toShort())
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, blockAlign.toShort())
        writeShortLE(header, 34, bitsPerSample.toShort())
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, dataSize)

        return header + pcmData
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = (value shr 8 and 0xFF).toByte()
        buf[offset + 2] = (value shr 16 and 0xFF).toByte()
        buf[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Short) {
        buf[offset] = (value.toInt() and 0xFF).toByte()
        buf[offset + 1] = (value.toInt() shr 8 and 0xFF).toByte()
    }
}
