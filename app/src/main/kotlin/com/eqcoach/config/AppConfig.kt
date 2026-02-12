package com.eqcoach.config

object AppConfig {
    // Server
    const val SERVER_URL = "http://192.168.1.195:8000"
    const val ANALYZE_ENDPOINT = "/analyze"
    const val TIMEOUT_SECONDS = 8L

    // Capture (used by EPIC-2)
    const val CAPTURE_INTERVAL_SECONDS = 4L
    const val CAPTURE_IMAGE_WIDTH = 640
    const val CAPTURE_IMAGE_HEIGHT = 480
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNELS = 1
    const val AUDIO_BIT_DEPTH = 16

    // UI
    const val COLOR_TRANSITION_MS = 250
}
