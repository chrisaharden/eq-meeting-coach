package com.eqcoach.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.eqcoach.config.AppConfig

/**
 * Continuously records audio from the device microphone using [AudioRecord],
 * storing PCM samples in a ring buffer. On demand, the latest chunk (default 4 s)
 * can be retrieved as a WAV-encoded byte array.
 *
 * Audio parameters are read from [AppConfig]:
 *   - Sample rate : 16 000 Hz
 *   - Channels    : 1 (mono)
 *   - Bit depth   : 16-bit PCM
 *
 * Usage:
 *   1. [start] — begins recording on a background thread.
 *   2. [getLatestChunk] — returns the most recent audio as a WAV byte array.
 *   3. [stop] — stops recording and releases resources.
 */
class AudioCapture {

    companion object {
        private const val TAG = "AudioCapture"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    // Ring-buffer sized for one full capture interval of PCM data.
    private val chunkSizeBytes: Int =
        AppConfig.AUDIO_SAMPLE_RATE *
            (AppConfig.AUDIO_BIT_DEPTH / 8) *
            AppConfig.AUDIO_CHANNELS *
            AppConfig.CAPTURE_INTERVAL_SECONDS.toInt()

    private val ringBuffer = ByteArray(chunkSizeBytes)
    private var ringPos = 0
    private var bufferFilled = false
    private val bufferLock = Object()

    /**
     * Begins recording audio from the microphone.
     *
     * @return [Result.success] if recording started, or [Result.failure] with
     *         an explanatory exception if the microphone is unavailable.
     */
    @SuppressLint("MissingPermission")
    fun start(): Result<Unit> {
        if (isRecording) return Result.success(Unit)

        return try {
            val channelConfig = if (AppConfig.AUDIO_CHANNELS == 1)
                AudioFormat.CHANNEL_IN_MONO
            else
                AudioFormat.CHANNEL_IN_STEREO

            val encoding = when (AppConfig.AUDIO_BIT_DEPTH) {
                16 -> AudioFormat.ENCODING_PCM_16BIT
                8 -> AudioFormat.ENCODING_PCM_8BIT
                else -> AudioFormat.ENCODING_PCM_16BIT
            }

            val minBufferSize = AudioRecord.getMinBufferSize(
                AppConfig.AUDIO_SAMPLE_RATE,
                channelConfig,
                encoding,
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                return Result.failure(
                    IllegalStateException("Microphone unavailable or unsupported audio configuration"),
                )
            }

            val bufferSize = maxOf(minBufferSize, chunkSizeBytes)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AppConfig.AUDIO_SAMPLE_RATE,
                channelConfig,
                encoding,
                bufferSize,
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return Result.failure(IllegalStateException("Failed to initialize AudioRecord"))
            }

            audioRecord = record
            isRecording = true
            record.startRecording()

            recordingThread = Thread({
                readLoop()
            }, "AudioCaptureThread").also { it.start() }

            Log.i(TAG, "Audio recording started")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stop()
            Result.failure(e)
        }
    }

    /**
     * Returns the latest audio chunk as a WAV-encoded byte array.
     *
     * If a full capture interval of audio has been buffered, the returned WAV
     * contains exactly that duration.  If less audio is available (e.g. just
     * after recording started), a shorter WAV is returned.
     *
     * @return WAV bytes, or null if no audio has been recorded yet.
     */
    fun getLatestChunk(): ByteArray? {
        synchronized(bufferLock) {
            if (!bufferFilled && ringPos == 0) return null

            val pcmData: ByteArray
            if (bufferFilled) {
                // Buffer has wrapped — copy oldest → newest in order.
                pcmData = ByteArray(chunkSizeBytes)
                val firstPartLen = chunkSizeBytes - ringPos
                System.arraycopy(ringBuffer, ringPos, pcmData, 0, firstPartLen)
                System.arraycopy(ringBuffer, 0, pcmData, firstPartLen, ringPos)
            } else {
                // Buffer not yet full — return what we have.
                pcmData = ByteArray(ringPos)
                System.arraycopy(ringBuffer, 0, pcmData, 0, ringPos)
            }

            return WavEncoder.encode(
                pcmData,
                AppConfig.AUDIO_SAMPLE_RATE,
                AppConfig.AUDIO_CHANNELS,
                AppConfig.AUDIO_BIT_DEPTH,
            )
        }
    }

    /**
     * Stops recording and releases all microphone resources. Safe to call multiple times.
     */
    fun stop() {
        isRecording = false
        try { recordingThread?.join(2000) } catch (_: InterruptedException) {}
        recordingThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        synchronized(bufferLock) {
            ringPos = 0
            bufferFilled = false
        }
        Log.i(TAG, "Audio recording stopped")
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun readLoop() {
        val readBuf = ByteArray(1024)
        while (isRecording) {
            val bytesRead = audioRecord?.read(readBuf, 0, readBuf.size) ?: -1
            if (bytesRead > 0) {
                synchronized(bufferLock) {
                    for (i in 0 until bytesRead) {
                        ringBuffer[ringPos] = readBuf[i]
                        ringPos = (ringPos + 1) % chunkSizeBytes
                        if (ringPos == 0) bufferFilled = true
                    }
                }
            }
        }
    }
}
