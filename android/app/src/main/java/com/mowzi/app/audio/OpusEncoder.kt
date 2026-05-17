package com.mowzi.app.audio

import io.element.android.opusencoder.OggOpusEncoder
import io.element.android.opusencoder.configuration.SampleRate
import java.io.File
import java.nio.ByteBuffer

/**
 * Opus encoder wrapper using io.element.android:opusencoder.
 * Encodes 16kHz mono PCM to Ogg/Opus format via file-based encoding.
 */
class OpusEncoder {
    private var encoder: OggOpusEncoder = OggOpusEncoder.create()
    private val frameSize: Int = 960 // 60ms at 16kHz
    private var initialized = false

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BITRATE = 32000
    }

    /**
     * Initializes the encoder to write to a file.
     * @param outputFile The output Ogg/Opus file
     */
    fun init(outputFile: File): Int {
        val result = encoder.init(outputFile.absolutePath, SampleRate.Rate16kHz)
        encoder.setBitrate(BITRATE)
        initialized = true
        return result
    }

    /**
     * Encodes PCM samples to the Ogg/Opus file.
     * @param pcmSamples PCM audio data as short array
     */
    fun encode(pcmSamples: ShortArray): Int {
        check(initialized) { "OpusEncoder not initialized. Call init() first." }
        return encoder.encode(pcmSamples, pcmSamples.size)
    }

    /**
     * Encodes a chunk of PCM data with proper byte-to-short conversion.
     * @param pcmData Raw PCM data as byte array (16-bit little-endian)
     */
    fun encodeFromBytes(pcmData: ByteArray): Int {
        val shorts = ShortArray(pcmData.size / 2)
        ByteBuffer.wrap(pcmData).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return encode(shorts)
    }

    /**
     * Releases encoder resources. Must be called when done encoding.
     */
    fun release() {
        if (initialized) {
            encoder.release()
            initialized = false
        }
    }
}