package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Owns the CameraX session for the in-app capture host: binds a chosen lens, refreshes
 * [CameraRuntimeCapabilities] after every bind or lens switch, and exposes imperative torch / zoom /
 * focus hooks. All Camera2 reads stay behind [CameraCapabilityProbe] so the host never touches
 * CameraInfo directly (S0545 §3.4).
 */
class CameraCaptureSessionManager(
    private val lifecycleOwner: LifecycleOwner,
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var availableLensFacings: List<Int> = emptyList()

    /** When true the session binds a video pipeline instead of image capture. Set before [bind]. */
    var videoMode: Boolean = false

    private val probe = CameraCapabilityProbe()

    /** Latest probed capabilities of the active lens; [CameraRuntimeCapabilities.NONE] before bind. */
    var capabilities: CameraRuntimeCapabilities = CameraRuntimeCapabilities.NONE
        private set

    /** Invoked on the main thread after every successful bind / lens switch. */
    var onCapabilitiesChanged: ((CameraRuntimeCapabilities) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun bind(
        previewView: PreviewView,
        onReady: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        this.previewView = previewView
        val context = previewView.context
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    availableLensFacings = probe.availableLensFacings(provider)
                    if (availableLensFacings.isNotEmpty() && lensFacing !in availableLensFacings) {
                        lensFacing = availableLensFacings.first()
                    }
                    bindToLifecycle(provider, previewView)
                    onReady()
                }.onFailure { error ->
                    Timber.e(error, "CameraCaptureSessionManager: bind failed")
                    onError(error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Flips to the next available lens and rebinds; no-op when only one lens exists. */
    @SuppressLint("MissingPermission")
    fun switchCamera() {
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        if (availableLensFacings.size < 2) return
        val nextIndex = (availableLensFacings.indexOf(lensFacing) + 1) % availableLensFacings.size
        lensFacing = availableLensFacings[nextIndex]
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: lens switch failed") }
    }

    /**
     * Switches between the photo and video pipelines in-screen (S0563) and rebinds with the matching
     * CameraX use-case set. No-op when the mode is unchanged or the session is not bound yet; stops any
     * active recording first so a rebuild never lands mid-record. Re-probes capabilities via
     * [bindToLifecycle], so control visibility refreshes through [onCapabilitiesChanged].
     */
    @SuppressLint("MissingPermission")
    fun applyMode(videoMode: Boolean) {
        if (this.videoMode == videoMode) return
        this.videoMode = videoMode
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        if (isRecording()) stopRecording()
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: mode switch failed") }
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun setTorchEnabled(enabled: Boolean) {
        if (capabilities.hasFlashUnit) camera?.cameraControl?.enableTorch(enabled)
    }

    /** Tap-to-focus at the given preview coordinates; ignored when the active lens cannot focus. */
    fun startFocusAndMetering(x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        val preview = previewView ?: return
        if (!capabilities.supportsTapToFocus) return
        val point = preview.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
            .build()
        runCatching { control.startFocusAndMetering(action) }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: focus failed") }
    }

    fun capture(
        previewView: PreviewView,
        outputFile: File,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("Camera is not bound"))
            return
        }

        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(previewView.context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSaved()

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "CameraCaptureSessionManager: capture failed")
                    onError(exception)
                }
            },
        )
    }

    fun unbind() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        videoCapture = null
        camera = null
        previewView = null
    }

    fun isRecording(): Boolean = activeRecording != null

    /**
     * Starts a video recording into [outputFile]. Audio is enabled only when [withAudio] is true,
     * which the caller gates on an explicit RECORD_AUDIO grant (S0545 §3.3, ADR-5) - never silently.
     * [onFinalized] receives whether the recording ended with an error.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, withAudio: Boolean, onFinalized: (Boolean) -> Unit) {
        val capture = videoCapture
        val context = previewView?.context
        if (capture == null || context == null) {
            onFinalized(true)
            return
        }
        if (activeRecording != null) return

        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        var pending = capture.output.prepareRecording(context, outputOptions)
        if (withAudio) pending = pending.withAudioEnabled()
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    Timber.e("CameraCaptureSessionManager: recording finalize error ${event.error}")
                }
                activeRecording = null
                onFinalized(event.hasError())
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    /** S0566: pauses an in-flight recording (CameraX keeps the file open); no-op when not recording. */
    fun pauseRecording() {
        runCatching { activeRecording?.pause() }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: pause failed") }
    }

    /** S0566: resumes a paused recording; no-op when not recording. */
    fun resumeRecording() {
        runCatching { activeRecording?.resume() }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: resume failed") }
    }

    private fun bindToLifecycle(provider: ProcessCameraProvider, previewView: PreviewView) {
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val captureUseCase: UseCase = if (videoMode) {
            val recorder = Recorder.Builder().build()
            VideoCapture.withOutput(recorder).also {
                videoCapture = it
                imageCapture = null
            }
        } else {
            ImageCapture.Builder().build().also {
                imageCapture = it
                videoCapture = null
            }
        }

        provider.unbindAll()
        val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, captureUseCase)

        camera = boundCamera
        capabilities = probe.probe(boundCamera, lensFacing, availableLensFacings)
        onCapabilitiesChanged?.invoke(capabilities)
    }

    companion object {
        private const val FOCUS_AUTO_CANCEL_SECONDS = 3L
    }
}
