package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Owns the CameraX session for the in-app capture host: binds a chosen lens, refreshes
 * [CameraRuntimeCapabilities] after every bind or lens switch, and exposes imperative torch / zoom /
 * focus hooks. All Camera2 reads stay behind [CameraCapabilityProbe] so the host never touches
 * CameraInfo directly (S0545 §3.4).
 *
 * S0753: the lens switch cycles every camera the device exposes to CameraX (e.g. back ultra-wide,
 * back main, back tele, front), not just one back + one front, so otherwise-unreachable lenses (and
 * their 0.5x / long-zoom ranges) become selectable; night mode uses the OEM NIGHT extension when
 * available and falls back to exposure compensation otherwise so the control works on every device.
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
    private var previewUseCase: Preview? = null

    /** Every camera CameraX exposes to the app, back lenses first; the switch button cycles this list. */
    private var availableCameras: List<CameraInfo> = emptyList()
    private var activeCameraIndex = 0

    /** Focal length of the main back lens, the 1x reference for equivalent-zoom labels (S0753). */
    private var referenceFocal = 0f

    /** When true the session binds a video pipeline instead of image capture. Set before [bind]. */
    var videoMode: Boolean = false

    private val probe = CameraCapabilityProbe()

    private var extensionsManager: ExtensionsManager? = null

    /** True when the active lens exposes the CameraX NIGHT extension; drives the extension vs fallback path. */
    private var nightExtensionAvailable = false

    /** True when the active lens exposes the CameraX HDR extension. */
    private var hdrExtensionAvailable = false

    /** S0753: night-mode intent; applied via the NIGHT extension or an exposure-compensation fallback. */
    var nightMode: Boolean = false
        private set

    /** S0754: HDR intent; applied by rebinding to the HDR extension selector when available. */
    var hdrEnabled: Boolean = false
        private set

    /** S0753: macro (close-focus) intent for the active lens; applied live via Camera2 capture options. */
    var macroEnabled: Boolean = false
        private set

    /** Device-driven target rotation of the locked portrait host; updated by CameraOrientationManager. */
    private var targetRotation: Int = Surface.ROTATION_0

    /** Live camera settings state mirrored into the dialog. */
    private var exposureCompensationIndex = 0
    private var whiteBalanceMode: Int? = null
    private var manualIso: Int? = null
    private var manualShutterNs: Long? = null
    private var selectedAspectRatio: Int? = null
    private var selectedResolution: Size? = null

    /** S0753: digital (crop) zoom factor on top of the optical/CameraX max; 1 = no digital crop. */
    private var digitalZoomFactor = 1f

    /**
     * Off-main thread for the digital-zoom JPEG crop so capture never blocks the UI. Created lazily on
     * the first crop (most shots are not digital-zoom) and released in [unbind] so a closed session
     * never leaks the worker thread (S0767).
     */
    private var cropExecutor: ExecutorService? = null

    /**
     * S1066: bakes a digital-zoom recording's soft crop into the MP4 after finalize so the saved video
     * matches the zoomed preview (owner Q1). Idle unless the recording used digital zoom.
     */
    private val videoZoomProcessor = VideoDigitalZoomProcessor()

    /** Latest probed capabilities of the active lens; [CameraRuntimeCapabilities.NONE] before bind. */
    var capabilities: CameraRuntimeCapabilities = CameraRuntimeCapabilities.NONE
        private set

    /** Invoked on the main thread after every successful bind / lens switch. */
    var onCapabilitiesChanged: ((CameraRuntimeCapabilities) -> Unit)? = null

    /**
     * S1066: overlays (e.g. the result frame) that must scale in lockstep with the preview under the
     * soft digital zoom, so they keep marking the same region of the digitally-zoomed image.
     */
    var previewScaleLinkedViews: List<View> = emptyList()

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
                    availableCameras = probe.availableCameras(provider)
                    // Start on the first back camera (the device's main wide), front cameras come last.
                    activeCameraIndex =
                        availableCameras.indexOfFirst { it.lensFacing == CameraSelector.LENS_FACING_BACK }
                            .coerceAtLeast(0)
                    referenceFocal = probe.mainBackFocalLength(availableCameras)
                    Timber.i(
                        "CameraCapture: %d camera(s) exposed to CameraX, facings=%s",
                        availableCameras.size,
                        availableCameras.map { it.lensFacing },
                    )
                    // S0753: the NIGHT extension needs the ExtensionsManager ready before binding; a
                    // null manager just means the extension is unavailable, not a bind failure.
                    val extFuture = ExtensionsManager.getInstanceAsync(context, provider)
                    extFuture.addListener(
                        {
                            extensionsManager = runCatching { extFuture.get() }.getOrNull()
                            runCatching {
                                bindToLifecycle(provider, previewView)
                                onReady()
                            }.onFailure { error ->
                                Timber.e(error, "CameraCaptureSessionManager: bind failed")
                                onError(error)
                            }
                        },
                        ContextCompat.getMainExecutor(context),
                    )
                }.onFailure { error ->
                    Timber.e(error, "CameraCaptureSessionManager: bind failed")
                    onError(error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Cycles to the next camera CameraX exposes and rebinds; no-op when only one camera exists. */
    @SuppressLint("MissingPermission")
    fun switchCamera() {
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        if (availableCameras.size < 2) return
        activeCameraIndex = (activeCameraIndex + 1) % availableCameras.size
        // A different physical lens has its own capabilities, so drop stale night/macro intents.
        nightMode = false
        hdrEnabled = false
        macroEnabled = false
        whiteBalanceMode = null
        manualIso = null
        manualShutterNs = null
        exposureCompensationIndex = 0
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
        // S0753: night mode is photo-only, so leaving photo mode drops the night intent.
        if (videoMode) nightMode = false
        if (videoMode) hdrEnabled = false
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        if (isRecording()) stopRecording()
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: mode switch failed") }
    }

    /**
     * S0753: toggles night mode (photo only). When the active lens exposes the NIGHT extension a rebind
     * swaps in the extension selector; otherwise it applies a strong positive exposure compensation,
     * which lengthens exposure / raises gain in a dark scene without needing a rebind.
     */
    @SuppressLint("MissingPermission")
    fun applyNightMode(enabled: Boolean) {
        if (nightMode == enabled) return
        if (enabled) hdrEnabled = false
        nightMode = enabled
        if (nightExtensionAvailable) {
            val provider = cameraProvider ?: return
            val preview = previewView ?: return
            runCatching { bindToLifecycle(provider, preview) }
                .onFailure { Timber.e(it, "CameraCaptureSessionManager: night mode switch failed") }
        } else {
            applyExposureCompensationForNight()
        }
    }

    @SuppressLint("MissingPermission")
    fun applyHdr(enabled: Boolean) {
        if (videoMode || hdrEnabled == enabled) return
        if (enabled) nightMode = false
        hdrEnabled = enabled
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: HDR switch failed") }
    }

    private fun applyExposureCompensationForNight() {
        val index = if (nightMode) capabilities.maxExposureCompensationIndex else 0
        exposureCompensationIndex = index
        runCatching { camera?.cameraControl?.setExposureCompensationIndex(index) }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: exposure compensation failed") }
    }

    /** S0753: toggles macro by locking the active lens to its closest focus distance, applied live. */
    fun applyMacro(enabled: Boolean) {
        macroEnabled = enabled
        applyCamera2Options()
    }

    fun setExposureCompensation(index: Int) {
        if (!capabilities.supportsExposureCompensation || manualIso != null || manualShutterNs != null) return
        exposureCompensationIndex = index.coerceIn(
            -capabilities.maxExposureCompensationIndex,
            capabilities.maxExposureCompensationIndex,
        )
        runCatching { camera?.cameraControl?.setExposureCompensationIndex(exposureCompensationIndex) }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: exposure compensation failed") }
    }

    fun setWhiteBalance(mode: Int) {
        whiteBalanceMode = if (mode == CameraMetadata.CONTROL_AWB_MODE_AUTO) null else mode
        applyCamera2Options()
    }

    fun setManualSensor(iso: Int, exposureNs: Long) {
        val isoRange = capabilities.isoRange ?: return
        val shutterRange = capabilities.shutterRangeNs ?: return
        if (!capabilities.supportsManualSensor) return
        manualIso = iso.coerceIn(isoRange.lower, isoRange.upper)
        manualShutterNs = exposureNs.coerceIn(shutterRange.lower, shutterRange.upper)
        exposureCompensationIndex = 0
        runCatching { camera?.cameraControl?.setExposureCompensationIndex(0) }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: reset exposure for manual sensor failed") }
        applyCamera2Options()
    }

    fun clearManualSensor() {
        manualIso = null
        manualShutterNs = null
        applyCamera2Options()
    }

    fun setAspectRatioAndResolution(aspectRatio: Int?, resolution: Size?) {
        val changed = selectedAspectRatio != aspectRatio || selectedResolution != resolution
        selectedAspectRatio = aspectRatio
        selectedResolution = resolution
        if (!changed) return
        val provider = cameraProvider ?: return
        val preview = previewView ?: return
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: output format switch failed") }
    }

    fun currentExposureCompensationIndex(): Int = exposureCompensationIndex

    fun currentWhiteBalanceMode(): Int? = whiteBalanceMode

    fun currentManualIso(): Int? = manualIso

    fun currentManualShutterNs(): Long? = manualShutterNs

    fun currentAspectRatio(): Int? = selectedAspectRatio

    fun currentResolution(): Size? = selectedResolution

    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
        previewUseCase?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    fun setZoomRatio(ratio: Float) {
        val opticalRatio = ratio.coerceAtMost(capabilities.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(opticalRatio)
        // S0753: beyond the optical/CameraX max, keep zooming by scaling (cropping) the preview.
        digitalZoomFactor = if (opticalRatio > 0f) (ratio / opticalRatio).coerceAtLeast(1f) else 1f
        applyDigitalZoomScale(digitalZoomFactor)
    }

    /** S0753: linear (0..1) zoom for the perceptually-linear slider; stays within the optical range. */
    fun setLinearZoom(linear: Float) {
        camera?.cameraControl?.setLinearZoom(linear)
        resetDigitalZoom()
    }

    private fun resetDigitalZoom() {
        digitalZoomFactor = 1f
        applyDigitalZoomScale(1f)
    }

    /** S1066: scales the preview and every preview-linked overlay (result frame) together, so the frame
     *  keeps marking the same image region under the soft digital zoom. */
    private fun applyDigitalZoomScale(scale: Float) {
        previewView?.let {
            it.scaleX = scale
            it.scaleY = scale
        }
        previewScaleLinkedViews.forEach {
            it.scaleX = scale
            it.scaleY = scale
        }
    }

    /** S0753: resulting ratio after the last zoom change, so presets and slider can mirror each other. */
    fun currentZoomRatio(): Float =
        camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: CameraRuntimeCapabilities.DEFAULT_ZOOM

    /** S0753: resulting 0..1 linear position after the last zoom change. */
    fun currentLinearZoom(): Float =
        camera?.cameraInfo?.zoomState?.value?.linearZoom ?: 0f

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
        location: android.location.Location? = null,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("Camera is not bound"))
            return
        }

        capture.targetRotation = targetRotation
        val builder = ImageCapture.OutputFileOptions.Builder(outputFile)
        // S0766: opt-in geotag. CameraX writes GPS into the JPEG EXIF before any digital-zoom crop,
        // and the crop path preserves the GPS tags (PRESERVED_EXIF_TAGS, S0765), so a cropped shot
        // keeps the same coordinates. A null location (setting off / no permission / no fix) leaves
        // the photo without GPS - the shutter is never gated on a location.
        if (location != null) {
            builder.setMetadata(ImageCapture.Metadata().apply { this.location = location })
        }
        val outputOptions = builder.build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(previewView.context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // S0753: match the saved photo to a digital (soft) zoom by cropping it off-thread.
                    // S1066: and, in photo mode, crop the full 4:3 sensor frame down to the selected
                    // 16:9 result frame so the file equals the on-screen frame. Both run on the crop
                    // worker so capture never blocks the UI; 4:3 needs neither.
                    val factor = digitalZoomFactor
                    val needsAspectCrop = !videoMode && selectedAspectRatio == AspectRatio.RATIO_16_9
                    if (factor > 1f || needsAspectCrop) {
                        val executor = cropExecutor
                            ?: Executors.newSingleThreadExecutor().also { cropExecutor = it }
                        executor.execute {
                            if (factor > 1f) cropCenter(outputFile, factor)
                            if (needsAspectCrop) cropToSixteenNine(outputFile)
                            ContextCompat.getMainExecutor(previewView.context).execute { onSaved() }
                        }
                    } else {
                        onSaved()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "CameraCaptureSessionManager: capture failed")
                    onError(exception)
                }
            },
        )
    }

    /** S0753: center-crops a captured JPEG by [factor] to match the digital (soft) zoom, overwriting it. */
    private fun cropCenter(file: File, factor: Float) {
        runCatching {
            // S0765: snapshot the EXIF CameraX wrote before we overwrite the file. Bitmap.compress
            // emits a bare JPEG with no metadata, which silently dropped orientation/datetime/GPS for
            // every digital-zoom shot. A center crop keeps the pixels in their stored (sensor)
            // orientation, so the original tags - including TAG_ORIENTATION - remain valid as-is.
            val originalExif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()

            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return
            val w = decoder.width
            val h = decoder.height
            val cropW = (w / factor).toInt().coerceAtLeast(1)
            val cropH = (h / factor).toInt().coerceAtLeast(1)
            val left = (w - cropW) / 2
            val top = (h - cropH) / 2
            val region = decoder.decodeRegion(Rect(left, top, left + cropW, top + cropH), null)
            decoder.recycle()
            val scaled = Bitmap.createScaledBitmap(region, w, h, true)
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (scaled != region) region.recycle()
            scaled.recycle()
            originalExif?.let { restoreExif(it, file) }
        }.onFailure { Timber.w(it, "CameraCaptureSessionManager: digital-zoom crop failed") }
    }

    /**
     * S1066: centre-crops a captured JPEG to the 16:9 result frame inside the full 4:3 sensor frame the
     * ViewPort delivered, so the saved file equals the on-screen result frame. The sensor JPEG is
     * stored landscape, so 16:9 (wider than 4:3) keeps the full long edge and trims the short edge;
     * the pixels stay in their stored orientation, so TAG_ORIENTATION and the other tags remain valid
     * and are re-applied via [restoreExif] (S0765). A frame already at or below 16:9 is left untouched.
     */
    private fun cropToSixteenNine(file: File) {
        runCatching {
            val originalExif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()

            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return
            val w = decoder.width
            val h = decoder.height
            val longEdge = maxOf(w, h)
            val shortEdge = minOf(w, h)
            val targetShort = (longEdge.toLong() * SIXTEEN_NINE_SHORT / SIXTEEN_NINE_LONG).toInt()
            if (targetShort >= shortEdge) {
                decoder.recycle()
                return
            }
            val rect = if (w >= h) {
                val top = (h - targetShort) / 2
                Rect(0, top, w, top + targetShort)
            } else {
                val left = (w - targetShort) / 2
                Rect(left, 0, left + targetShort, h)
            }
            val cropped = decoder.decodeRegion(rect, null)
            decoder.recycle()
            FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            cropped.recycle()
            originalExif?.let { restoreExif(it, file) }
        }.onFailure { Timber.w(it, "CameraCaptureSessionManager: aspect crop failed") }
    }

    /**
     * S0765: re-applies the [source] EXIF tags onto [target] after a re-encode that drops metadata.
     * Orientation is copied unchanged (the crop did not physically rotate pixels), so a viewer rotates
     * the cropped shot exactly like the original full-frame capture.
     */
    private fun restoreExif(source: ExifInterface, target: File) {
        runCatching {
            val dest = ExifInterface(target.absolutePath)
            PRESERVED_EXIF_TAGS.forEach { tag ->
                source.getAttribute(tag)?.let { value -> dest.setAttribute(tag, value) }
            }
            dest.saveAttributes()
        }.onFailure { Timber.w(it, "CameraCaptureSessionManager: EXIF restore after crop failed") }
    }

    fun unbind() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        previewUseCase = null
        imageCapture = null
        videoCapture = null
        camera = null
        previewView = null
        // S1066: drop the overlay references so a closed session never pins a destroyed view.
        previewScaleLinkedViews = emptyList()
        // S0767: orderly shutdown() (never shutdownNow) lets an in-flight crop finish writing its JPEG
        // and releases the worker thread deterministically instead of waiting for GC, without blocking
        // the calling (main) thread; nulling the field lets a later bind()+crop recreate it.
        cropExecutor?.shutdown()
        cropExecutor = null
        // S1066: cancel any in-flight digital-zoom re-encode so a closed session never leaks it.
        videoZoomProcessor.release()
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
                activeRecording = null
                // S1066: a digital-zoom recording is wider than the preview (CameraX records the full
                // ViewPort FOV), so re-encode the finished file with the same centred crop before
                // signalling the host, keeping the callback contract WYSIWYG for video like it is for
                // photos. Runs only when soft zoom was used; onFinalized fires once the file is final.
                val factor = digitalZoomFactor
                when {
                    event.hasError() -> {
                        Timber.e("CameraCaptureSessionManager: recording finalize error ${event.error}")
                        onFinalized(true)
                    }

                    factor > 1f -> videoZoomProcessor.crop(context, outputFile, factor) { onFinalized(false) }
                    else -> onFinalized(false)
                }
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
        val activeInfo = availableCameras.getOrNull(activeCameraIndex) ?: run {
            Timber.e("CameraCaptureSessionManager: no camera at index $activeCameraIndex")
            return
        }
        val useCases = CameraUseCaseFactory(
            videoMode = videoMode,
            selectedAspectRatio = selectedAspectRatio,
            selectedResolution = selectedResolution,
            targetRotation = targetRotation,
        ).create(previewView)
        previewUseCase = useCases.preview
        imageCapture = useCases.imageCapture
        videoCapture = useCases.videoCapture
        val baseSelector = CameraUseCaseFactory.selectorFor(activeInfo)
        // S0753: NIGHT is photo-only and per-device; fall back to exposure compensation when unavailable.
        nightExtensionAvailable = !videoMode &&
            extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) == true
        hdrExtensionAvailable = !videoMode &&
            extensionsManager?.isExtensionAvailable(baseSelector, ExtensionMode.HDR) == true
        val selector = when {
            hdrEnabled && hdrExtensionAvailable ->
                extensionsManager!!.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
            nightMode && nightExtensionAvailable ->
                extensionsManager!!.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
            else -> {
                baseSelector
            }
        }
        provider.unbindAll()
        val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, useCases.group)

        camera = boundCamera
        resetDigitalZoom()
        val probed = probe.probe(
            boundCamera,
            activeInfo.lensFacing,
            availableCameras.map { it.lensFacing },
            referenceFocal,
        )
        // Night mode is offered when either the OEM extension or exposure compensation can deliver it.
        val supportsNight = nightExtensionAvailable || probed.supportsExposureCompensation
        capabilities = probed.copy(
            supportsNightMode = supportsNight,
            supportsHdrExtension = hdrExtensionAvailable,
        )
        if (!capabilities.supportsManualSensor) {
            manualIso = null
            manualShutterNs = null
        }
        if (!capabilities.supportsHdrExtension) hdrEnabled = false
        onCapabilitiesChanged?.invoke(capabilities)
        // A rebind resets exposure compensation, so re-apply the night offset on the fallback path.
        if (nightMode && !nightExtensionAvailable) applyExposureCompensationForNight()
        else if (manualIso == null && manualShutterNs == null && capabilities.supportsExposureCompensation) {
            setExposureCompensation(exposureCompensationIndex)
        }
        applyCamera2Options()
    }

    /**
     * S1066: true when a result frame must be drawn over the preview - photo mode with a selected ratio
     * narrower than the full sensor frame (16:9 inside 4:3). Video previews the recorded region, so no
     * frame; 4:3 fills the shown frame, so no frame (spec ADR-1, §6.4).
     */
    fun shouldShowResultFrame(): Boolean =
        !videoMode && selectedAspectRatio == AspectRatio.RATIO_16_9

    private fun applyCamera2Options() {
        val control = camera?.cameraControl ?: return
        val c2 = Camera2CameraControl.from(control)
        runCatching {
            c2.clearCaptureRequestOptions()
            val builder = CaptureRequestOptions.Builder()
            var hasOptions = false
            if (manualIso != null && manualShutterNs != null) {
                builder
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIso!!)
                    .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualShutterNs!!)
                hasOptions = true
            }
            if (macroEnabled && capabilities.macroFocusDistance > 0f) {
                builder
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, capabilities.macroFocusDistance)
                hasOptions = true
            }
            whiteBalanceMode?.let {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, it)
                hasOptions = true
            }
            if (hasOptions) c2.setCaptureRequestOptions(builder.build())
        }.onFailure { Timber.w(it, "CameraCaptureSessionManager: Camera2 options apply failed") }
    }

    companion object {
        private const val FOCUS_AUTO_CANCEL_SECONDS = 3L
        private const val JPEG_QUALITY = 95

        private const val SIXTEEN_NINE_LONG = 16
        private const val SIXTEEN_NINE_SHORT = 9

        /**
         * S0765: EXIF tags carried across the digital-zoom re-encode so a cropped shot keeps the same
         * rotation, capture time and place as the original full-frame JPEG CameraX produced.
         */
        private val PRESERVED_EXIF_TAGS = listOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
        )
    }
}
