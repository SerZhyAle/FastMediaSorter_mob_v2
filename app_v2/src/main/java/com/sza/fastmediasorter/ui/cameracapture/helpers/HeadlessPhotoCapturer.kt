package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

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
 *
 * S1360: the shot owns its lifecycle. It used to bind to the caller's, so anything that stopped the
 * host aborted the in-flight frame with `ImageCaptureException: Camera is closed` by way of
 * `LifecycleCameraRepositoryObserver.onStop` - Android 16 declining a background activity start, an
 * orientation relayout, any system priority decision. The host lifecycle is still observed, but only
 * as a last-resort release, never as the thing that ends the capture.
 */
class HeadlessPhotoCapturer(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
) {

    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * S1478: the same device-angle source the camera screen uses. The icon callback is empty on
     * purpose - a headless shot draws no overlay, so there is nothing to keep upright.
     */
    private val orientationManager = CameraOrientationManager(
        context = context,
        onIconRotationChanged = {},
        onTargetRotationChanged = ::applyTargetRotation,
    )

    /**
     * S1478: the use case whose rotation follows the device, from the bind until the shutter. Cleared
     * before [ImageCapture.takePicture] so a turn during the exposure cannot re-target a frame that is
     * already being captured.
     */
    private var rotatingCapture: ImageCapture? = null

    // S1360: CameraX binds to this, not to the host. Nothing outside this class can move it.
    private val captureLifecycle = CaptureLifecycleOwner()

    // S1360 safety net, deliberately NOT the primary mechanism: with the capture off the host's
    // lifecycle, a takePicture that never calls back would hold the camera open indefinitely. The
    // host being destroyed is the last moment anyone can free it.
    private val hostObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            release()
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(hostObserver)
    }

    /**
     * Captures a single photo into [outputFile]. [location] is written into the JPEG EXIF when the
     * caller opted into geotagging and holds a fix; null leaves the photo without GPS. Exactly one of
     * [onSaved] / [onError] is invoked on the main thread.
     *
     * S1478: [aspectRatio] is the stored `AspectRatio.RATIO_*` selection. The photo stream is always
     * 4:3, so 16:9 is realised by cropping the saved file (S1066) - the same way the camera screen
     * does it.
     */
    @SuppressLint("MissingPermission")
    fun capture(
        outputFile: File,
        location: Location?,
        aspectRatio: Int,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        // S1478: enabled before the provider is awaited, so the sensor has the binding latency to
        // report at least one reading before the use case is built.
        orientationManager.enable()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val lens = resolveLens(provider)
                    val bucket = orientationManager.rotationBucket.value
                    Timber.d("S1478: headless lens=%s rotation=%d aspect=%d", lens.id, bucket, aspectRatio)
                    val selector = CameraUseCaseFactory.selectorFor(lens)
                    // S1478: the same factory the camera screen builds its use cases with, so the two
                    // capture routes share one definition of output geometry instead of two builders.
                    // S1189: the selector binds the logical camera; a chosen sub-lens travels here.
                    val imageCapture = CameraUseCaseFactory(
                        videoMode = false,
                        selectedAspectRatio = null,
                        selectedResolution = null,
                        targetRotation = bucket,
                        physicalCameraId = lens.physicalCameraId,
                    ).createPhotoCapture()
                    rotatingCapture = imageCapture
                    // Bind ONLY ImageCapture (no Preview) so CameraX opens the device without any UI.
                    provider.unbindAll()
                    // S1360: RESUMED before the bind - CameraX only opens the device for an owner
                    // that is at least STARTED, and this registry is the one it will watch.
                    captureLifecycle.resume()
                    provider.bindToLifecycle(captureLifecycle, selector, imageCapture)
                    Timber.d("S1360: bound to own capture lifecycle, host stop cannot abort the frame")
                    takePicture(imageCapture, outputFile, location, aspectRatio, onSaved, onError)
                }.onFailure { error ->
                    Timber.e(error, "HeadlessPhotoCapturer: provider/bind failed")
                    release()
                    onError(error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * S1478: the lens the camera screen would open, resolved by the same rule - the enumeration plus
     * [CameraLensEnumerationManager.initialLensIndex], which deliberately steers the initial choice
     * off the ultra-wide lens (S1261 defect D1). CameraX's default back-camera constant used to land
     * the two capture routes on different optics of the same device.
     */
    private fun resolveLens(provider: ProcessCameraProvider): CameraLensEntry {
        val enumeration = CameraLensEnumerationManager()
        val entries = enumeration.select(enumeration.expand(provider))
        return entries.getOrNull(enumeration.initialLensIndex(entries))
            ?: throw IllegalStateException("No camera available")
    }

    private fun takePicture(
        imageCapture: ImageCapture,
        outputFile: File,
        location: Location?,
        aspectRatio: Int,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        // S1478: the rotation is now fixed for this frame - see [rotatingCapture].
        rotatingCapture = null
        val optionsBuilder = ImageCapture.OutputFileOptions.Builder(outputFile)
        location?.let {
            optionsBuilder.setMetadata(ImageCapture.Metadata().apply { this.location = it })
        }
        imageCapture.takePicture(
            optionsBuilder.build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Released before the crop so the camera device is not held while a JPEG is
                    // decoded and re-encoded.
                    release()
                    cropThenReport(outputFile, aspectRatio, onSaved)
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "HeadlessPhotoCapturer: capture failed")
                    release()
                    onError(exception)
                }
            },
        )
    }

    private fun applyTargetRotation(rotation: Int) {
        rotatingCapture?.targetRotation = rotation
    }

    /**
     * S1478: applies the 16:9 crop off the main thread, then reports success there. A crop failure is
     * already swallowed inside the cropper: the uncropped photo is saved, and losing it would be worse
     * than wrong proportions.
     */
    private fun cropThenReport(outputFile: File, aspectRatio: Int, onSaved: () -> Unit) {
        if (aspectRatio != AspectRatio.RATIO_16_9) {
            onSaved()
            return
        }
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            CapturedPhotoAspectCropper.cropToSixteenNine(outputFile)
            ContextCompat.getMainExecutor(context).execute { onSaved() }
        }
        // Shut down straight after submitting: an already-queued task still runs, and the worker thread
        // cannot outlive the shot even if the crop above ever starts throwing.
        worker.shutdown()
    }

    /** Releases the camera device; safe to call more than once (lifecycle teardown + capture end). */
    fun release() {
        rotatingCapture = null
        orientationManager.disable()
        runCatching { lifecycleOwner.lifecycle.removeObserver(hostObserver) }
            .onFailure { Timber.w(it, "HeadlessPhotoCapturer: host observer removal failed") }
        runCatching { cameraProvider?.unbindAll() }
            .onFailure { Timber.w(it, "HeadlessPhotoCapturer: unbind failed") }
        cameraProvider = null
        // S1360: destroying the registry is what actually tells CameraX the use case is done. Last,
        // so an unbind failure above cannot leave the owner alive with no provider to release it.
        captureLifecycle.destroy()
    }

    /**
     * S1360: the capture's own lifecycle. Exists so the frame in flight survives every external stop
     * of the host - only [release] ends it.
     */
    private class CaptureLifecycleOwner : LifecycleOwner {

        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = registry

        fun resume() {
            // A registry that already reached DESTROYED cannot go back up, and LifecycleRegistry
            // throws rather than ignoring the attempt.
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.currentState = Lifecycle.State.RESUMED
            }
        }

        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
