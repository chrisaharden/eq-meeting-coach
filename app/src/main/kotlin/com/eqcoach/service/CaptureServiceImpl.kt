package com.eqcoach.service

import android.content.Context
import android.util.Log
import com.eqcoach.capture.AudioCapture
import com.eqcoach.capture.CameraCapture
import com.eqcoach.model.Verdict
import com.eqcoach.network.AnalyzeClient
import com.eqcoach.network.AnalyzeResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Concrete implementation of [CaptureService] that coordinates camera frame
 * capture and microphone audio capture.
 */
class CaptureServiceImpl(private val context: Context) : CaptureService {

    companion object {
        private const val TAG = "CaptureServiceImpl"
        private const val WAV_HEADER_SIZE = 44
    }

    private val cameraCapture = CameraCapture(context)
    private val audioCapture = AudioCapture()
    private val analyzeClient = AnalyzeClient()

    @Volatile
    private var isActive = false

    @Volatile
    private var currentVerdict: Verdict = Verdict.GRAY

    @Volatile
    override var lastFrameData: ByteArray? = null
        private set

    @Volatile
    override var audioLevel: Float = 0f
        private set

    @Volatile
    var latestAudioData: ByteArray? = null
        private set

    @Volatile
    var startError: String? = null
        private set

    override fun startCapture() {
        if (isActive) return
        startError = null

        if (cameraCapture.findFrontCameraId() == null) {
            startError = "No front-facing camera available"
            Log.e(TAG, startError!!)
            return
        }

        try {
            cameraCapture.start()
        } catch (e: Exception) {
            startError = "Camera start failed: ${e.message}"
            Log.e(TAG, startError!!, e)
            return
        }

        val audioResult = audioCapture.start()
        if (audioResult.isFailure) {
            startError = "Microphone unavailable: ${audioResult.exceptionOrNull()?.message}"
            Log.e(TAG, startError!!)
            cameraCapture.stop()
            return
        }

        isActive = true
        Log.i(TAG, "Capture started")
    }

    override fun stopCapture() {
        isActive = false
        analyzeClient.cancelInflight()
        cameraCapture.stop()
        audioCapture.stop()
        lastFrameData = null
        latestAudioData = null
        audioLevel = 0f
        currentVerdict = Verdict.GRAY
        Log.i(TAG, "Capture stopped")
    }

    override suspend fun getCurrentResult(): AnalyzeResponse? {
        if (!isActive) return null

        val frame = cameraCapture.captureFrame()
        lastFrameData = frame

        val audio = audioCapture.getLatestChunk()
        latestAudioData = audio
        audioLevel = computeRms(audio)

        if (frame == null || audio == null) return null

        val result = analyzeClient.analyze(frame, audio)
        currentVerdict = result.verdict
        return result
    }

    /**
     * Compute RMS (0.0–1.0) from 16-bit PCM WAV bytes.
     * Skips the 44-byte WAV header, reads samples as little-endian Short.
     */
    private fun computeRms(wavBytes: ByteArray?): Float {
        if (wavBytes == null || wavBytes.size <= WAV_HEADER_SIZE) return 0f
        val pcm = ByteBuffer.wrap(wavBytes, WAV_HEADER_SIZE, wavBytes.size - WAV_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        var sumSquares = 0.0
        val count = pcm.remaining()
        for (i in 0 until count) {
            val sample = pcm.get().toDouble() / Short.MAX_VALUE
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / count).toFloat().coerceIn(0f, 1f)
    }
}
