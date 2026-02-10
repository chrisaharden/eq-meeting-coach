package com.eqcoach.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {

    @Test
    fun `encode produces 44-byte header plus PCM data`() {
        val pcm = ByteArray(100) { it.toByte() }
        val wav = WavEncoder.encode(pcm, sampleRate = 16000, channels = 1, bitDepth = 16)
        assertEquals(44 + 100, wav.size)
    }

    @Test
    fun `header starts with RIFF and contains WAVE marker`() {
        val wav = WavEncoder.encode(ByteArray(0), 16000, 1, 16)
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])

        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])
    }

    @Test
    fun `chunk size field equals file size minus 8`() {
        val pcm = ByteArray(256)
        val wav = WavEncoder.encode(pcm, 16000, 1, 16)
        val chunkSize = readLittleEndianInt(wav, 4)
        assertEquals(wav.size - 8, chunkSize)
    }

    @Test
    fun `fmt sub-chunk has correct PCM parameters for mono 16-bit 16kHz`() {
        val wav = WavEncoder.encode(ByteArray(0), 16000, 1, 16)
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        // fmt sub-chunk starts at byte 12
        assertEquals('f'.code.toByte(), wav[12])
        assertEquals('m'.code.toByte(), wav[13])
        assertEquals('t'.code.toByte(), wav[14])
        assertEquals(' '.code.toByte(), wav[15])

        buf.position(16)
        assertEquals(16, buf.int)          // sub-chunk size
        assertEquals(1.toShort(), buf.short) // audio format (PCM)
        assertEquals(1.toShort(), buf.short) // channels
        assertEquals(16000, buf.int)         // sample rate
        assertEquals(32000, buf.int)         // byte rate = 16000 * 1 * 2
        assertEquals(2.toShort(), buf.short) // block align = 1 * 2
        assertEquals(16.toShort(), buf.short) // bits per sample
    }

    @Test
    fun `fmt sub-chunk has correct parameters for stereo 16-bit 44100 Hz`() {
        val wav = WavEncoder.encode(ByteArray(0), 44100, 2, 16)
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(22)
        assertEquals(2.toShort(), buf.short) // channels
        assertEquals(44100, buf.int)         // sample rate
        assertEquals(176400, buf.int)        // byte rate = 44100 * 2 * 2
        assertEquals(4.toShort(), buf.short) // block align = 2 * 2
        assertEquals(16.toShort(), buf.short)
    }

    @Test
    fun `data sub-chunk size matches PCM length`() {
        val pcm = ByteArray(1024) { (it % 256).toByte() }
        val wav = WavEncoder.encode(pcm, 16000, 1, 16)
        val dataSize = readLittleEndianInt(wav, 40)
        assertEquals(1024, dataSize)
    }

    @Test
    fun `PCM payload is preserved verbatim after header`() {
        val pcm = ByteArray(64) { (it * 3).toByte() }
        val wav = WavEncoder.encode(pcm, 16000, 1, 16)
        val payload = wav.copyOfRange(44, wav.size)
        assertArrayEquals(pcm, payload)
    }

    @Test
    fun `empty PCM produces valid 44-byte WAV`() {
        val wav = WavEncoder.encode(ByteArray(0), 16000, 1, 16)
        assertEquals(44, wav.size)
        val dataSize = readLittleEndianInt(wav, 40)
        assertEquals(0, dataSize)
    }

    // ── Helpers ──

    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
    }
}
