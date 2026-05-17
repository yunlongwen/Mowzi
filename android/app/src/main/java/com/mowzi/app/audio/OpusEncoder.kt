package com.mowzi.app.audio

import org.concentus.OpusEncoder as ConcentusOpusEncoder
import org.concentus.OpusApplication
import java.nio.ByteBuffer

/**
 * Opus encoder wrapper for PCM to Opus compression.
 * Encodes 16kHz mono PCM to Opus frames.
 */
class OpusEncoder {
    private val encoder: ConcentusOpusEncoder
    private val frameSize: Int = 960 // 60ms at 16kHz

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1
        private const val BITRATE = 32000
    }

    init {
        encoder = ConcentusOpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP)
        encoder.bitrate = BITRATE
    }

    /**
     * Encodes PCM samples to Opus bytes.
     * @param pcmSamples PCM audio data as short array
     * @return Encoded Opus frame as byte array
     */
    fun encode(pcmSamples: ShortArray): ByteArray {
        val outputBuffer = ByteArray(frameSize * 4) // Oversize buffer
        val encodedBytes = encoder.encode(pcmSamples, pcmSamples.size, outputBuffer, 0, outputBuffer.size)
        return outputBuffer.copyOf(encodedBytes)
    }

    /**
     * Encodes a chunk of PCM data with proper interleaving.
     * @param pcmData Raw PCM data as byte array (16-bit little-endian)
     * @return Encoded Opus frame
     */
    fun encodeFromBytes(pcmData: ByteArray): ByteArray {
        val shorts = ShortArray(pcmData.size / 2)
        ByteBuffer.wrap(pcmData).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return encode(shorts)
    }
}