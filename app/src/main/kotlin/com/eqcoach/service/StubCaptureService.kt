package com.eqcoach.service

import com.eqcoach.model.Verdict

/**
 * Stub implementation for the Android shell.
 * Always returns GRAY; replaced by the real Capture Agent in EPIC-2.
 */
class StubCaptureService : CaptureService {
    override fun startCapture() { /* no-op */ }
    override fun stopCapture() { /* no-op */ }
    override suspend fun getCurrentVerdict(): Verdict = Verdict.GRAY
}
