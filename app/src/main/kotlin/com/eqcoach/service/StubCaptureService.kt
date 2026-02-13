package com.eqcoach.service

import com.eqcoach.network.AnalyzeResponse

/**
 * Stub implementation for the Android shell.
 * Always returns null; replaced by the real Capture Agent in EPIC-2.
 */
class StubCaptureService : CaptureService {
    override fun startCapture() { /* no-op */ }
    override fun stopCapture() { /* no-op */ }
    override suspend fun getCurrentResult(): AnalyzeResponse? = null
    override val lastFrameData: ByteArray? = null
    override val audioLevel: Float = 0f
}
