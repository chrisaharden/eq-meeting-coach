package com.eqcoach.service

import com.eqcoach.network.AnalyzeResponse

/**
 * Interface for the capture-and-analyze loop.
 * The Android Capture Agent (EPIC-2) will provide the implementation.
 * The SessionViewModel invokes start/stop; the implementation handles
 * camera frames, audio recording, server communication, and emitting verdicts.
 */
interface CaptureService {
    fun startCapture()
    fun stopCapture()
    suspend fun getCurrentResult(): AnalyzeResponse?

    /** Most recently captured JPEG frame (for debug preview). */
    val lastFrameData: ByteArray?

    /** Audio RMS level 0.0–1.0 from most recent capture. */
    val audioLevel: Float
}
