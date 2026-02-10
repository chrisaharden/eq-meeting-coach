package com.eqcoach.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.eqcoach.config.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Captures JPEG frames from the front-facing camera using the Camera2 API.
 *
 * Usage:
 *   1. Call [start] to open the camera and prepare capture session (non-blocking, async init).
 *   2. Call [captureFrame] to grab a single JPEG-encoded frame (suspend, returns when ready).
 *   3. Call [stop] to release all camera resources.
 */
class CameraCapture(private val context: Context) {

    companion object {
        private const val TAG = "CameraCapture"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var dummyTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null

    @Volatile
    private var isReady = false

    @Volatile
    private var startError: String? = null

    /**
     * Returns the camera ID of the front-facing camera, or null if none exists.
     */
    fun findFrontCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    /**
     * Opens the front-facing camera and prepares a capture session.
     * This method is non-blocking; the camera initializes asynchronously on a background thread.
     * Check [isReady] or call [captureFrame] (which returns null if not ready).
     *
     * @throws IllegalStateException if no front-facing camera is found.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        val cameraId = findFrontCameraId()
            ?: throw IllegalStateException("No front-facing camera available")

        startError = null
        isReady = false

        startBackgroundThread()
        setupImageReader()
        setupDummySurface()
        openCameraAsync(cameraId)
    }

    /**
     * Captures a single JPEG frame from the front camera.
     * Returns the JPEG byte array, or null if the camera is not yet ready or capture fails.
     */
    suspend fun captureFrame(): ByteArray? {
        if (!isReady) return null

        val device = cameraDevice ?: return null
        val session = captureSession ?: return null
        val reader = imageReader ?: return null

        return suspendCancellableCoroutine { cont ->
            reader.setOnImageAvailableListener({ imgReader ->
                val image = imgReader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        if (cont.isActive) cont.resume(bytes)
                    } finally {
                        image.close()
                    }
                } else {
                    if (cont.isActive) cont.resume(null)
                }
            }, backgroundHandler)

            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }.build()
                session.capture(request, null, backgroundHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Capture request failed", e)
                if (cont.isActive) cont.resume(null)
            }

            cont.invokeOnCancellation {
                reader.setOnImageAvailableListener(null, null)
            }
        }
    }

    /**
     * Releases all camera resources. Safe to call multiple times.
     */
    fun stop() {
        isReady = false
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { dummySurface?.release() } catch (_: Exception) {}
        dummySurface = null
        try { dummyTexture?.release() } catch (_: Exception) {}
        dummyTexture = null
        stopBackgroundThread()
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try { backgroundThread?.join(2000) } catch (_: InterruptedException) {}
        backgroundThread = null
        backgroundHandler = null
    }

    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(
            AppConfig.CAPTURE_IMAGE_WIDTH,
            AppConfig.CAPTURE_IMAGE_HEIGHT,
            ImageFormat.JPEG,
            2
        )
    }

    private fun setupDummySurface() {
        dummyTexture = SurfaceTexture(0).also {
            it.setDefaultBufferSize(1, 1)
        }
        dummySurface = Surface(dummyTexture)
    }

    @SuppressLint("MissingPermission")
    private fun openCameraAsync(cameraId: String) {
        cameraManager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSessionAsync()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    isReady = false
                    Log.w(TAG, "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    isReady = false
                    startError = "Camera error code: $error"
                    Log.e(TAG, "Camera error: $error")
                }
            },
            backgroundHandler
        )
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSessionAsync() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val dummy = dummySurface ?: return

        device.createCaptureSession(
            listOf(dummy, reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    // Keep camera active with a repeating preview on the dummy surface.
                    val previewRequest = device.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW
                    ).apply {
                        addTarget(dummy)
                    }.build()
                    session.setRepeatingRequest(previewRequest, null, backgroundHandler)
                    isReady = true
                    Log.i(TAG, "Camera ready")
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    startError = "Failed to configure camera session"
                    Log.e(TAG, "Session configuration failed")
                }
            },
            backgroundHandler
        )
    }
}
