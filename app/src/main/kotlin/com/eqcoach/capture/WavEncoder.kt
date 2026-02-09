package com.eqcoach.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes raw PCM byte data into a WAV byte array (RIFF/WAVE container).
 *
 * WAV file layout (44-byte header + PCM data):
 *   Bytes 0-3   : "RIFF"
 *   Bytes 4-7   : file size − 8
 *   Bytes 8-11  : "WAVE"
 *   Bytes 12-15 : "fmt "
 *   Bytes 16-19 : 16 (fmt sub-chunk size for PCM)
 *   Bytes 20-21 : 1  (audio format = PCM)
 *   Bytes 22-23 : channels
 *   Bytes 24-27 : sample rate
 *   Bytes 28-31 : byte rate
 *   Bytes 32-33 : block align
 *   Bytes 34-35 : bits per sample
 *   Bytes 36-39 : "data"
 *   Bytes 40-43 : data size
 *   Bytes 44+   : PCM samples
 */
object WavEncoder {

    private const val HEADER_SIZE = 44
    private const val PCM_FORMAT: Short = 1

    /**
     * Wraps raw PCM bytes into a complete WAV byte array.
     *
     * @param pcmData    Raw PCM sample bytes (little-endian).
     * @param sampleRate Sample rate in Hz (e.g. 16000).
     * @param channels   Number of channels (1 = mono, 2 = stereo).
     * @param bitDepth   Bits per sample (8 or 16).
     * @return A valid WAV file as a byte array.
     */
    fun encode(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val blockAlign = channels * (bitDepth / 8)
        val byteRate = sampleRate * blockAlign
        val dataSize = pcmData.size
        val fileSize = HEADER_SIZE - 8 + dataSize // "RIFF" chunk size = total − 8

        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            // ── RIFF header ──
            put('R'.code.toByte())
            put('I'.code.toByte())
            put('F'.code.toByte())
            put('F'.code.toByte())
            putInt(fileSize)
            put('W'.code.toByte())
            put('A'.code.toByte())
            put('V'.code.toByte())
            put('E'.code.toByte())

            // ── fmt sub-chunk ──
            put('f'.code.toByte())
            put('m'.code.toByte())
            put('t'.code.toByte())
            put(' '.code.toByte())
            putInt(16)                       // sub-chunk size (PCM = 16)
            putShort(PCM_FORMAT)             // audio format
            putShort(channels.toShort())     // channels
            putInt(sampleRate)               // sample rate
            putInt(byteRate)                 // byte rate
            putShort(blockAlign.toShort())   // block align
            putShort(bitDepth.toShort())     // bits per sample

            // ── data sub-chunk ──
            put('d'.code.toByte())
            put('a'.code.toByte())
            put('t'.code.toByte())
            put('a'.code.toByte())
            putInt(dataSize)

            // ── PCM samples ──
            put(pcmData)
        }

        return buffer.array()
    }
}
