package com.eqcoach.service

import com.eqcoach.model.Verdict

/**
 * Interface for the capture-and-analyze loop.
 * The Android Capture Agent (EPIC-2) will provide the implementation.
 * The SessionViewModel invokes start/stop; the implementation handles
 * camera frames, audio recording, server communication, and emitting verdicts.
 */
interface CaptureService {
    fun startCapture()
    fun stopCapture()
    suspend fun getCurrentVerdict(): Verdict
}
