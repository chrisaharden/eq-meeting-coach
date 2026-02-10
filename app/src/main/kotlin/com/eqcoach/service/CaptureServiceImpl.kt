package com.eqcoach.service

import android.content.Context
import android.util.Log
import com.eqcoach.capture.AudioCapture
import com.eqcoach.capture.CameraCapture
import com.eqcoach.model.Verdict
import com.eqcoach.network.AnalyzeClient

/**
 * Concrete implementation of [CaptureService] that coordinates camera frame
 * capture and microphone audio capture.
 *
 * Wave 1 (STORY-2.1 & STORY-2.2): captures frames and audio and exposes them
 * for inspection. The verdict is always [Verdict.GRAY] until server
 * communication is added in Wave 2 (STORY-2.3).
 *
 * Wave 2 (STORY-2.3): [getCurrentVerdict] POSTs frame + audio to the inference
 * server via [AnalyzeClient] and returns the parsed verdict.
 */
class CaptureServiceImpl(private val context: Context) : CaptureService {

    companion object {
        private const val TAG = "CaptureServiceImpl"
    }

    private val cameraCapture = CameraCapture(context)
    private val audioCapture = AudioCapture()
    private val analyzeClient = AnalyzeClient()

    @Volatile
    private var isActive = false

    @Volatile
    private var currentVerdict: Verdict = Verdict.GRAY

    /** Most recently captured JPEG frame (available for Wave 2). */
    @Volatile
    var latestFrameData: ByteArray? = null
        private set

    /** Most recently captured WAV audio chunk (available for Wave 2). */
    @Volatile
    var latestAudioData: ByteArray? = null
        private set

    /** Non-null if camera or mic failed to start. */
    @Volatile
    var startError: String? = null
        private set

    override fun startCapture() {
        if (isActive) return
        startError = null

        // Validate front camera availability synchronously.
        if (cameraCapture.findFrontCameraId() == null) {
            startError = "No front-facing camera available"
            Log.e(TAG, startError!!)
            return
        }

        // Start camera (initialises asynchronously on background thread).
        try {
            cameraCapture.start()
        } catch (e: Exception) {
            startError = "Camera start failed: ${e.message}"
            Log.e(TAG, startError!!, e)
            return
        }

        // Start audio recording.
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
        latestFrameData = null
        latestAudioData = null
        currentVerdict = Verdict.GRAY
        Log.i(TAG, "Capture stopped")
    }

    /**
     * Grabs the latest camera frame and audio chunk, POSTs them to the
     * inference server, and returns the parsed verdict.
     *
     * Returns [Verdict.GRAY] if capture data is not yet available (camera
     * still initializing or no audio buffered). Throws [ServerException] on
     * network/server errors so the caller can surface feedback to the user.
     */
    override suspend fun getCurrentVerdict(): Verdict {
        if (!isActive) return Verdict.GRAY

        val frame = cameraCapture.captureFrame()
        latestFrameData = frame

        val audio = audioCapture.getLatestChunk()
        latestAudioData = audio

        if (frame == null || audio == null) return Verdict.GRAY

        val verdict = analyzeClient.analyze(frame, audio)
        currentVerdict = verdict
        return verdict
    }
}
