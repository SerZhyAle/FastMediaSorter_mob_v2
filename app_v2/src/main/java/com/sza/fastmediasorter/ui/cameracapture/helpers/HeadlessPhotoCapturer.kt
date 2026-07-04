package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.io.File

/**
 * S0790-S0794: headless single-shot photo capture for the edge-gesture "take photo" family.
 *
 * Binds ONLY a CameraX [ImageCapture] use case (no [androidx.camera.core.Preview]) to the caller's
 * lifecycle, so no camera screen is shown: the edge gesture takes a photo without opening a visible
 * camera activity. This replaces the previous flow that launched the full [CameraCaptureActivity] in
 * auto-capture mode - which both flashed the camera UI and lost its result to the trampoline's
 * `android:noHistory` teardown.
 *
 * Trade-offs the caller accepts:
 * - The system "camera in use" indicator (Android 12+) still shows briefly - unavoidable for any
 *   in-app camera access.
 * - The shot fires as soon as the provider binds, so the first frame has no time for full 3A
 *   (auto-exposure / auto-focus) convergence; this is the cost of not running a preview stream.
 *
 * The caller guarantees the CAMERA runtime grant before [capture]. The camera device is released as
 * soon as the shot resolves (or on any failure), and [release] is idempotent for lifecycle teardown.
 */
class HeadlessPhotoCapturer(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
) {

    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Captures a single photo into [outputFile]. [location] is written into the JPEG EXIF when the
     * caller opted into geotagging and holds a fix; null leaves the photo without GPS. Exactly one of
     * [onSaved] / [onError] is invoked on the main thread.
     */
    @SuppressLint("MissingPermission")
    fun capture(
        outputFile: File,
        location: Location?,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val selector = resolveSelector(provider)
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    // Bind ONLY ImageCapture (no Preview) so CameraX opens the device without any UI.
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, imageCapture)
                    takePicture(imageCapture, outputFile, location, onSaved, onError)
                }.onFailure { error ->
                    Timber.e(error, "HeadlessPhotoCapturer: provider/bind failed")
                    release()
                    onError(error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun resolveSelector(provider: ProcessCameraProvider): CameraSelector = when {
        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> throw IllegalStateException("No camera available")
    }

    private fun takePicture(
        imageCapture: ImageCapture,
        outputFile: File,
        location: Location?,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val optionsBuilder = ImageCapture.OutputFileOptions.Builder(outputFile)
        location?.let {
            optionsBuilder.setMetadata(ImageCapture.Metadata().apply { this.location = it })
        }
        imageCapture.takePicture(
            optionsBuilder.build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    release()
                    onSaved()
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "HeadlessPhotoCapturer: capture failed")
                    release()
                    onError(exception)
                }
            },
        )
    }

    /** Releases the camera device; safe to call more than once (lifecycle teardown + capture end). */
    fun release() {
        runCatching { cameraProvider?.unbindAll() }
            .onFailure { Timber.w(it, "HeadlessPhotoCapturer: unbind failed") }
        cameraProvider = null
    }
}
