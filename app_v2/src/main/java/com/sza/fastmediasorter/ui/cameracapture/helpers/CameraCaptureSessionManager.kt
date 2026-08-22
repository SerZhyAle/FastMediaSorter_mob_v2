package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.cameracapture.model.CameraAspectSelection
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * S1189: every lens the switch button cycles - the logical cameras plus each physical sub-lens
     * that reaches a magnification none of them covers.
     */
    private var availableLenses: List<CameraLensEntry> = emptyList()
    private var activeCameraIndex = 0

    private val lensEnumeration = CameraLensEnumerationManager()

    /** When true the session binds a video pipeline instead of image capture. Set before [bind]. */
    var videoMode: Boolean = false

    private val probe = CameraCapabilityProbe()

    private var extensionsManager: ExtensionsManager? = null

    /**
     * S1579: extension availability per "lens id + video mode". CameraX reads the vendor extension
     * config from disk on every `isExtensionAvailable`, and every rebind - settings apply, mode
     * switch, lens switch - asked again for an answer that cannot change between binds. Concurrent
     * because the host warms it off the main thread while binds read it on the main thread.
     */
    private val offeredExtensionsCache = ConcurrentHashMap<String, CameraExtensionSelector.Intents>()

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

    /** S1262: portrait intent; applied by rebinding to the BOKEH extension selector when available. */
    var bokehEnabled: Boolean = false
        private set

    /** S1262: sport intent; applied live via [SportExposureOptionsFactory]'s Camera2 capture options. */
    var sportEnabled: Boolean = false
        private set

    /** S1262: BOKEH availability on the bound lens, refreshed on every bind like its NIGHT/HDR siblings. */
    private var bokehExtensionAvailable: Boolean = false

    /**
     * S1189: the lens active before macro switched to dedicated close-focus optics; null while macro
     * is off, or while it is running on the focus-lock fallback (no lens change happened).
     */
    private var lensBeforeMacro: CameraLensEntry? = null

    /** Device-driven target rotation of the locked portrait host; updated by CameraOrientationManager. */
    private var targetRotation: Int = Surface.ROTATION_0

    /** Live camera settings state mirrored into the dialog. */
    private var exposureCompensationIndex = 0
    private var whiteBalanceMode: Int? = null
    private var manualIso: Int? = null
    private var manualShutterNs: Long? = null
    private var selectedAspect: CameraAspectSelection? = null
    private var selectedResolution: Size? = null

    /** S0753: digital (crop) zoom factor on top of the optical/CameraX max; 1 = no digital crop. */
    private var digitalZoomFactor = 1f

    /**
     * S1457: lens id of the last successful bind. A rebind on the SAME lens must keep the zoom the
     * user dialled in; a lens switch must not, because the new optics own their zoom range.
     */
    private var lastBoundLensId: String? = null

    // S1457: the optical ratio the user last asked for on the bound lens. A rebind cannot read this back
    // from the session: in video mode this handset answers zoomState with 1.0 while its own preview is
    // genuinely zoomed, so carrying zoomState across photo -> video -> photo returned the user to 1x.
    // Cleared on a lens change, where starting at the new lens default is the wanted behaviour.
    private var requestedZoomRatio: Float? = null

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

    /** S1658: the lens being left, reported before the switch clears its intents, so it can be saved. */
    var onLensLeaving: ((lensId: String) -> Unit)? = null

    /**
     * S1658: the lens being entered, reported after the reset and before the bind - the one moment a
     * remembered set can be written back without costing a second rebind. Never fired for a
     * profile-driven switch, which would otherwise overwrite the profile the user just picked.
     */
    var onLensEntering: ((lensId: String) -> Unit)? = null

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
                    availableLenses = lensEnumeration.select(lensEnumeration.expand(provider))
                    // S1261 (defect D1): start on the MAIN back lens, not the widest - the list is
                    // sorted widest-first, so "first back" used to open on the ultra-wide entry whose
                    // own floor is 1.0, and the sub-1x pill vanished from the zoom row.
                    activeCameraIndex = lensEnumeration.initialLensIndex(availableLenses)
                    Timber.i(
                        "CameraCapture: %d lens(es) offered, %d of them physical sub-lenses",
                        availableLenses.size,
                        availableLenses.count { it.isPhysicalSubLens },
                    )
                    // S0753: the NIGHT extension needs the ExtensionsManager ready before binding; a
                    // null manager just means the extension is unavailable, not a bind failure.
                    val extFuture = ExtensionsManager.getInstanceAsync(context, provider)
                    extFuture.addListener(
                        {
                            extensionsManager = runCatching { extFuture.get() }.getOrNull()
                            // S1579: the first bind asked the vendor extension config from disk on the
                            // main thread, and a warm-up started from onReady can never overtake it.
                            // Only the pair this bind will ask for is warmed here - warming every pair
                            // would hold the first preview frame for the cost of all of them.
                            lifecycleOwner.lifecycleScope.launch {
                                withContext(Dispatchers.IO) { warmOfferedExtensions(onlyActiveLens = true) }
                                runCatching {
                                    bindToLifecycle(provider, previewView)
                                    onReady()
                                }.onFailure { error ->
                                    Timber.e(error, "CameraCaptureSessionManager: bind failed")
                                    onError(error)
                                }
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

    /**
     * Cycles to the next camera CameraX exposes and rebinds; no-op when only one camera exists.
     *
     * S1261: [targetEquivalentFloor] switches instead to the lens that reaches that equivalent zoom
     * (preferring a sub-1x logical camera, so the platform swaps optics itself) and lands the zoom
     * on it in the same action - the cross-lens floor pill's contract. Ignored when null.
     */
    @SuppressLint("MissingPermission")
    fun switchCamera(targetEquivalentFloor: Float? = null) {
        val provider = cameraProvider
        val preview = previewView
        if (provider == null || preview == null || availableLenses.size < 2) return
        val target = targetEquivalentFloor?.let { lensReaching(availableLenses, it) }
        if (targetEquivalentFloor != null && target == null) return
        val targetIndex = target?.let { t -> availableLenses.indexOfFirst { it.id == t.id } }
        val nativeRatio = targetEquivalentFloor?.let { floor ->
            target?.takeIf { it.equivalentMultiplier > 0f }?.let { floor / it.equivalentMultiplier }
        }
        bindLens(provider, preview, targetIndex ?: ((activeCameraIndex + 1) % availableLenses.size))
        // Already on the reaching lens: no rebind flicker, just land the zoom.
        nativeRatio?.let(::setZoomRatio)
    }

    /**
     * The probe reports what the bound lens itself can do; everything the session knows on top of that
     * - extensions, the device-wide lens picture - is folded in here. Extracted from the bind path so
     * that path stays inside its length budget as the snapshot grows (S1675 added the rear-lens floors).
     */
    private fun decorateCapabilities(
        probed: CameraRuntimeCapabilities,
        activeLens: CameraLensEntry,
    ): CameraRuntimeCapabilities = probed.copy(
        // Night mode is offered when either the OEM extension or exposure compensation can deliver it.
        supportsNightMode = nightExtensionAvailable || probed.supportsExposureCompensation,
        supportsHdrExtension = hdrExtensionAvailable,
        supportsBokehExtension = bokehExtensionAvailable,
        minEquivalentZoomRatio = probe.minEquivalentZoom(availableLenses),
        rearLensEquivalentFloors = rearLensFloors(),
        macroLensAvailable = probe.macroLensFor(availableLenses, activeLens.lensFacing) != null,
        activeLensIsWidest = isWidestOfFacing(activeLens, availableLenses),
        // S1581: the label needs dedicated macro optics, not merely the closest-focusing lens -
        // otherwise a device without a macro lens calls its own main camera one.
        activeLensIsMacro = probe.isDedicatedMacroLens(availableLenses, activeLens),
    )

    /**
     * S1675: the printed floors of the rear lenses, for the pill row shown where the bound lens has no
     * zoom range of its own. Empty below two rear lenses - one pill would offer a switch to nowhere.
     */
    private fun rearLensFloors(): List<Float> {
        val floors = availableLenses
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .map { it.minZoomRatio * it.equivalentMultiplier }
        val displayed = CameraRuntimeCapabilities.buildRearLensFloors(floors)
        return if (displayed.size > 1) displayed else emptyList()
    }

    /**
     * S1262: moves to a lens with the given [facing] in one action. [switchCamera] cycles, which
     * lands on the wrong optics on a device with several back lenses - a profile names the lens it
     * wants. BACK resolves to the MAIN back lens (S1261 defect D1), not the widest one the sorted
     * list starts with. No-op when the device offers no lens of that facing.
     */
    @SuppressLint("MissingPermission")
    fun switchToFacing(facing: Int, restoreSaved: Boolean = true) {
        val provider = cameraProvider
        val preview = previewView
        if (provider == null || preview == null) return
        val targetIndex = if (facing == CameraSelector.LENS_FACING_BACK) {
            lensEnumeration.initialLensIndex(availableLenses)
        } else {
            availableLenses.indexOfFirst { it.lensFacing == facing }
        }
        if (availableLenses.getOrNull(targetIndex)?.lensFacing != facing) return
        bindLens(provider, preview, targetIndex, restoreSaved)
    }

    /**
     * Binds [targetIndex] as the active lens, or does nothing when it is already bound.
     *
     * A different physical lens has its own capabilities, so every per-lens intent is dropped first -
     * BOKEH availability and the usable shutter range are both per-lens, so carrying them across
     * would promise a profile the new lens may not have. S1658 makes that reset the baseline rather
     * than the outcome: [onLensEntering] writes the entered lens's own remembered set over it, unless
     * [restoreSaved] is false because a profile - not the user - asked for this switch.
     */
    @SuppressLint("MissingPermission")
    private fun bindLens(
        provider: ProcessCameraProvider,
        preview: PreviewView,
        targetIndex: Int,
        restoreSaved: Boolean = true,
    ) {
        // S1479: resolved here as well as in the bind itself, so cycling onto a sub-lens the video
        // pipeline cannot carry is a no-op instead of a rebind that drops every per-lens intent.
        val resolvedIndex = availableLenses.bindableIndex(targetIndex, videoMode)
        if (resolvedIndex == activeCameraIndex) return
        availableLenses.getOrNull(activeCameraIndex)?.id?.let { onLensLeaving?.invoke(it) }
        activeCameraIndex = resolvedIndex
        nightMode = false
        hdrEnabled = false
        macroEnabled = false
        bokehEnabled = false
        sportEnabled = false
        whiteBalanceMode = null
        manualIso = null
        manualShutterNs = null
        exposureCompensationIndex = 0
        if (restoreSaved) availableLenses.getOrNull(resolvedIndex)?.id?.let { onLensEntering?.invoke(it) }
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: lens switch failed") }
    }

    /**
     * S1658: writes a remembered set onto the session without rebinding. Called from [onLensEntering],
     * between the reset above and the bind that follows, so the imminent bind picks up the profile
     * intents and the Camera2 options in one pass. HDR stays out: it is a toggle, not a profile.
     */
    fun restorePerLensState(saved: CameraLensSettingsMemory.LensSettings) {
        nightMode = saved.profile == PhotoProfile.NIGHT
        bokehEnabled = saved.profile == PhotoProfile.PORTRAIT
        macroEnabled = saved.profile == PhotoProfile.MACRO
        sportEnabled = saved.profile == PhotoProfile.SPORT
        whiteBalanceMode = saved.whiteBalanceMode
        manualIso = saved.manualIso
        manualShutterNs = saved.manualShutterNs
        exposureCompensationIndex = saved.exposureCompensationIndex
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
        // S1262: the profile intents are photo-only for the same reason.
        if (videoMode) {
            nightMode = false
            hdrEnabled = false
            bokehEnabled = false
            sportEnabled = false
        }
        val provider = cameraProvider
        val preview = previewView
        if (provider == null || preview == null) return
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
            val provider = cameraProvider
            val preview = previewView
            if (provider == null || preview == null) return
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
        val provider = cameraProvider
        val preview = previewView
        if (provider == null || preview == null) return
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: HDR switch failed") }
    }

    /**
     * S1262: sets both photo-profile intents in one call. BOKEH binds a CameraX extension, so a
     * change to it needs a rebind; sport is pure Camera2 capture options and only needs those
     * re-applied. An unchanged pair is a no-op, which is what keeps the profile manager's clear
     * sweep from rebinding the session for an intent it never turned on.
     */
    @SuppressLint("MissingPermission")
    fun applyProfileIntents(bokeh: Boolean, sport: Boolean) {
        val wantBokeh = bokeh && !videoMode
        val wantSport = sport && !videoMode
        if (bokehEnabled == wantBokeh && sportEnabled == wantSport) return
        val bokehChanged = bokehEnabled != wantBokeh
        bokehEnabled = wantBokeh
        sportEnabled = wantSport
        val provider = cameraProvider
        val preview = previewView
        if (!bokehChanged || provider == null || preview == null) {
            applyCamera2Options()
            return
        }
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: bokeh switch failed") }
    }

    private fun applyExposureCompensationForNight() {
        val index = if (nightMode) capabilities.maxExposureCompensationIndex else 0
        exposureCompensationIndex = index
        runCatching { camera?.cameraControl?.setExposureCompensationIndex(index) }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: exposure compensation failed") }
    }

    /**
     * S1189: macro prefers a dedicated close-focus lens, because that is how the hardware normally
     * implements it; locking the active lens to its closest focus distance (S0753) stays as the
     * fallback for devices that expose no such lens. Turning macro off returns to the lens the user
     * was on, so the toggle never strands them on different optics.
     */
    @SuppressLint("MissingPermission")
    fun applyMacro(enabled: Boolean) {
        macroEnabled = enabled
        val provider = cameraProvider
        val preview = previewView
        val activeFacing = availableLenses.getOrNull(activeCameraIndex)?.lensFacing
            ?: CameraSelector.LENS_FACING_BACK
        val macroIndex = if (enabled) {
            probe.macroLensFor(availableLenses, activeFacing)
                ?.let { lens -> availableLenses.indexOfFirst { it.id == lens.id } } ?: -1
        } else {
            -1
        }
        val restoreIndex = lensBeforeMacro
            ?.let { lens -> availableLenses.indexOfFirst { it.id == lens.id } } ?: -1
        val target = when {
            provider == null || preview == null -> NO_LENS_CHANGE
            enabled && macroIndex >= 0 && macroIndex != activeCameraIndex -> {
                lensBeforeMacro = availableLenses.getOrNull(activeCameraIndex)
                macroIndex
            }

            !enabled && restoreIndex >= 0 -> {
                lensBeforeMacro = null
                restoreIndex
            }

            else -> NO_LENS_CHANGE
        }
        if (provider == null || preview == null || target == NO_LENS_CHANGE) {
            applyCamera2Options()
            return
        }
        activeCameraIndex = target
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.w(it, "CameraCaptureSessionManager: macro lens change failed") }
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
        val isoRange = capabilities.isoRange
        val shutterRange = capabilities.shutterRangeNs
        if (!capabilities.supportsManualSensor || isoRange == null || shutterRange == null) return
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

    fun setAspectRatioAndResolution(selection: CameraAspectSelection?, resolution: Size?) {
        val changed = selectedAspect != selection || selectedResolution != resolution
        selectedAspect = selection
        selectedResolution = resolution
        if (!changed) return
        val provider = cameraProvider
        val preview = previewView
        if (provider == null || preview == null) return
        runCatching { bindToLifecycle(provider, preview) }
            .onFailure { Timber.e(it, "CameraCaptureSessionManager: output format switch failed") }
    }

    // Read-only mirrors of the private setting state. Properties rather than getters on purpose:
    // this class sits on detekt's TooManyFunctions ceiling, and property accessors do not count
    // toward it, which is what leaves room for the session primitives features actually need.
    val currentExposureCompensationIndex: Int get() = exposureCompensationIndex

    val currentWhiteBalanceMode: Int? get() = whiteBalanceMode

    val currentManualIso: Int? get() = manualIso

    val currentManualShutterNs: Long? get() = manualShutterNs

    val currentAspect: CameraAspectSelection? get() = selectedAspect

    /** S1658: the bound lens, so the host can key a per-lens memory off the identity S1457 already tracks. */
    val boundLensId: String? get() = lastBoundLensId

    /** S1658: every lens currently offered, so a memory entry for a lens that has left can be dropped. */
    val offeredLensIds: Set<String> get() = availableLenses.mapTo(mutableSetOf()) { it.id }

    val currentResolution: Size? get() = selectedResolution

    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
        previewUseCase?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    fun setZoomRatio(ratio: Float) {
        val opticalRatio = ratio.coerceAtMost(capabilities.maxZoomRatio)
        requestedZoomRatio = opticalRatio
        camera?.cameraControl?.setZoomRatio(opticalRatio)
        // S0753: beyond the optical/CameraX max, keep zooming by scaling (cropping) the preview.
        digitalZoomFactor = if (opticalRatio > 0f) (ratio / opticalRatio).coerceAtLeast(1f) else 1f
        applyDigitalZoomScale(digitalZoomFactor)
    }

    /** S0753: linear (0..1) zoom for the perceptually-linear slider; stays within the optical range. */
    fun setLinearZoom(linear: Float) {
        camera?.cameraControl?.setLinearZoom(linear)
        // The slider states its intent in linear terms, and the optical ratio it lands on is only known
        // from the session afterwards, so the tracked ratio is dropped and the carry falls back to
        // zoomState - which is accurate on the photo path this control belongs to.
        requestedZoomRatio = null
        resetDigitalZoom()
    }

    private fun resetDigitalZoom() {
        digitalZoomFactor = 1f
        applyDigitalZoomScale(1f)
    }

    /** S0753: scales the preview itself, which is how the soft digital zoom is shown. */
    private fun applyDigitalZoomScale(scale: Float) {
        previewView?.let {
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

    /**
     * Tap-to-focus at the given preview coordinates; ignored when the active lens cannot focus.
     *
     * S1419: returns whether a focus request was actually submitted, because the caller draws the
     * focus ring from this answer. Every early return below is a case where nothing was requested,
     * and returning Unit made all of them look identical to a real focus - the user saw the ring and
     * the lens never moved.
     */
    fun startFocusAndMetering(x: Float, y: Float): Boolean {
        val control = camera?.cameraControl
        val preview = previewView
        // S1189: while macro holds the lens at a fixed focus distance, an autofocus request silently
        // undoes it with no visible cue - so the tap is ignored rather than half-applied. The
        // lens-switch macro path keeps tap-to-focus (lensBeforeMacro is set only there).
        val macroHoldsFocus = macroEnabled && lensBeforeMacro == null && capabilities.macroFocusDistance > 0f
        val focusRefused = !capabilities.supportsTapToFocus || macroHoldsFocus
        // One branch rather than an early exit per reason: this class fails detekt at three returns
        // and again at four conditions, so the reasons are named above instead of inlined here.
        if (control == null || preview == null || focusRefused) {
            return false
        }
        val point = preview.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
            .build()
        return runCatching {
            control.startFocusAndMetering(action)
            true
        }.getOrElse {
            Timber.w(it, "CameraCaptureSessionManager: focus failed")
            false
        }
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
        // S1457: the crops below must match the frame the viewfinder showed at THIS shutter press.
        // onImageSaved lands hundreds of ms later with the zoom slider and pinch still live, so
        // reading the session fields there cropped the finished photo by whatever the user had moved
        // to since. Sampled once, here, and the callback reads nothing else.
        val zoomFactorAtShutter = digitalZoomFactor
        // S1658: only the full-screen selection still needs a crop - the other two are already the
        // shape the stream was requested at. Sampled here for the same reason as the zoom above.
        val cropRatioAtShutter = if (!videoMode && selectedAspect?.cropsToScreen == true) {
            // S1920: the shape of the view that was actually on screen, not of the display. The two part
            // company on system bars, a cutout and multi-window, and the file was being cropped to the
            // display while the user was looking at the view.
            // A zero edge means the view is not laid out yet, and only the display can answer for it.
            val metrics = previewView.resources.displayMetrics
            val laidOut = previewView.width > 0 && previewView.height > 0
            val cropWidth = if (laidOut) previewView.width else metrics.widthPixels
            val cropHeight = if (laidOut) previewView.height else metrics.heightPixels
            CapturedPhotoAspectCropper.ratioOfScreen(cropWidth, cropHeight)
        } else {
            null
        }
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
                    // S1658: and, on the full-screen selection, crop the 16:9 frame down to the shape
                    // of the screen it filled, so the file equals what the viewfinder showed. Both run
                    // on the crop worker so capture never blocks the UI.
                    if (zoomFactorAtShutter > 1f || cropRatioAtShutter != null) {
                        val executor = cropExecutor
                            ?: Executors.newSingleThreadExecutor().also { cropExecutor = it }
                        executor.execute {
                            if (zoomFactorAtShutter > 1f) {
                                CapturedPhotoAspectCropper.cropCenter(outputFile, zoomFactorAtShutter)
                            }
                            cropRatioAtShutter?.let { CapturedPhotoAspectCropper.cropToRatio(outputFile, it) }
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
                        handleFinalizeError(outputFile, event.error)
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
        activeCameraIndex = availableLenses.bindableIndex(activeCameraIndex, videoMode)
        val activeLens = availableLenses.getOrNull(activeCameraIndex) ?: run {
            Timber.e("CameraCaptureSessionManager: no lens at index $activeCameraIndex")
            return
        }
        // S1457: every settings apply - aspect, resolution, mode switch, HDR/night/bokeh/macro,
        // profiles - rebuilds the Camera object, and a fresh one always starts at the lens default.
        // Sampled before the unbind below, while the old camera can still report its zoom.
        if (lastBoundLensId != activeLens.id) requestedZoomRatio = null
        val carriedZoomRatio = if (lastBoundLensId == activeLens.id) requestedZoomRatio ?: currentZoomRatio() else null
        val useCases = CameraUseCaseFactory(
            videoMode = videoMode,
            selection = selectedAspect,
            selectedResolution = selectedResolution,
            targetRotation = targetRotation,
            physicalCameraId = activeLens.physicalCameraId,
            preferHighResolution = prefersHighResolution(
                selectedResolution,
                capabilities.highResolutionPhotoSizes,
            ),
        ).create(previewView)
        previewUseCase = useCases.preview
        imageCapture = useCases.imageCapture
        videoCapture = useCases.videoCapture
        val baseSelector = CameraUseCaseFactory.selectorFor(activeLens)
        // S0753: NIGHT falls back to exposure compensation when the lens does not offer it.
        // S1262: exactly one extension binds; the ranking lives in CameraExtensionSelector.
        // S1579: keyed by lens + mode (format shared with warmOfferedExtensions), so a switched lens
        // misses the map rather than being served a neighbour's answer.
        val offered = offeredExtensionsCache.getOrPut("${activeLens.id}|$videoMode") {
            extensionsManager.offeredExtensions(baseSelector, videoMode)
        }
        nightExtensionAvailable = offered.night
        hdrExtensionAvailable = offered.hdr
        bokehExtensionAvailable = offered.bokeh
        val wanted = CameraExtensionSelector.Intents(hdrEnabled, nightMode, bokehEnabled)
        val selector = CameraExtensionSelector.resolve(extensionsManager, baseSelector, wanted, offered)
        provider.unbindAll()
        val boundCamera = runCatching {
            provider.bindToLifecycle(lifecycleOwner, selector, useCases.group)
        }.getOrElse { error ->
            // S1189: an extended lens the device advertises but refuses to open must cost that lens,
            // not the whole capture screen (strategic ADR-3). Drop it and retry at the same facing,
            // ending at that facing's logical camera - the set the screen had before this ticket.
            // Inlined rather than extracted: the class already sits on detekt's 40-function ceiling.
            Timber.w(error, "CameraCaptureSessionManager: lens ${activeLens.id} refused to bind")
            availableLenses = availableLenses.filterNot { it.id == activeLens.id }
            val fallbackIndex = availableLenses.indexOfFirst { it.lensFacing == activeLens.lensFacing }
            if (fallbackIndex < 0) {
                Timber.e("CameraCaptureSessionManager: no lens left for facing ${activeLens.lensFacing}")
            } else {
                activeCameraIndex = fallbackIndex
                runCatching { bindToLifecycle(provider, previewView) }
                    .onFailure { Timber.e(it, "CameraCaptureSessionManager: fallback lens bind failed") }
            }
            return
        }

        camera = boundCamera
        lastBoundLensId = activeLens.id
        resetDigitalZoom()
        // S1457: restored before probing, so the capability snapshot the UI mirrors reports the ratio
        // the optics actually hold rather than the lens default.
        carriedZoomRatio?.let { boundCamera.restoreZoomRatio(it) }
        val probed = probe.probe(
            boundCamera,
            activeLens,
            availableLenses.map { it.lensFacing },
        )
        capabilities = decorateCapabilities(probed, activeLens)
        if (!capabilities.supportsManualSensor) {
            manualIso = null
            manualShutterNs = null
        }
        if (!capabilities.supportsHdrExtension) hdrEnabled = false
        onCapabilitiesChanged?.invoke(capabilities)
        // A rebind resets exposure compensation, so re-apply the night offset on the fallback path.
        if (nightMode && !nightExtensionAvailable) {
            applyExposureCompensationForNight()
        } else if (manualIso == null && manualShutterNs == null && capabilities.supportsExposureCompensation) {
            setExposureCompensation(exposureCompensationIndex)
        }
        applyCamera2Options()
    }

    /**
     * S1579: fills [offeredExtensionsCache] so a bind is served from the map instead of reading the
     * vendor extension config from disk on the main thread. [bind] warms the pair of the imminent
     * first bind ([onlyActiveLens]) off the main thread before binding; the host calls this again
     * with the default once the session is ready, covering the pairs a later mode or lens switch will
     * ask for. A lens list that changed since the caller started only costs an unwarmed entry, which
     * the next bind fills the same way it does today.
     */
    fun warmOfferedExtensions(onlyActiveLens: Boolean = false) {
        val manager = extensionsManager ?: return
        val active = availableLenses.getOrNull(availableLenses.bindableIndex(activeCameraIndex, videoMode))
        val lenses = if (onlyActiveLens) listOfNotNull(active) else availableLenses
        val modes = if (onlyActiveLens) listOf(videoMode) else listOf(false, true)
        lenses.forEach { lens ->
            val selector = CameraUseCaseFactory.selectorFor(lens)
            modes.forEach { mode ->
                // A vendor extension library that refuses an off-main query must cost the warm-up,
                // not the screen: the entry stays absent and the next bind fills it as it does today.
                runCatching {
                    offeredExtensionsCache.getOrPut("${lens.id}|$mode") {
                        manager.offeredExtensions(selector, mode)
                    }
                }.onFailure { Timber.w(it, "CameraCaptureSessionManager: extension warm-up failed for ${lens.id}") }
            }
        }
    }

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
            // S1262: sport freezes the exposure short and lets ISO rise to pay for it, with autofocus
            // left running because the subject moves. Gated on `manualIso == null` so an explicit
            // manual exposure - a deliberate choice - outranks a profile preset rather than fighting it.
            val sportApplies = sportEnabled && manualIso == null &&
                SportExposureOptionsFactory.isApplicable(capabilities)
            val sportExposureNs = SportExposureOptionsFactory.resolvedExposureNs(capabilities)
            val sportIso = SportExposureOptionsFactory.resolvedIso(capabilities)
            if (sportApplies && sportExposureNs != null && sportIso != null) {
                builder
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, sportExposureNs)
                    .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, sportIso)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                    )
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

        /** S1189: macro needs no lens change - apply it (or clear it) on the active lens instead. */
        private const val NO_LENS_CHANGE = -1
    }
}

/**
 * S1189: only the sensor's high-resolution sizes need the slower capture mode - an ordinary size
 * keeps today's latency. Lives outside the session class because it is a pure question about a size
 * and a list, and because the bind path is already at detekt's complexity ceiling.
 */
private fun prefersHighResolution(selected: Size?, highResolution: List<Size>): Boolean =
    selected != null && selected in highResolution

/**
 * S1261: the lens a cross-lens floor tap binds - a back lens whose reachable equivalent floor
 * (`minZoomRatio * equivalentMultiplier`) covers [equivalent], preferring a logical camera whose
 * own floor is below 1 (the platform then switches optics itself, exactly like the system camera)
 * and a logical entry over a physical sub-lens. Lives outside the session class because it is a
 * pure question about a list, and the class sits on detekt's 40-function ceiling.
 */
private fun lensReaching(lenses: List<CameraLensEntry>, equivalent: Float): CameraLensEntry? =
    lenses
        .filter {
            it.lensFacing == CameraSelector.LENS_FACING_BACK &&
                it.minZoomRatio * it.equivalentMultiplier <=
                equivalent + CameraRuntimeCapabilities.ZOOM_EPSILON
        }
        .minWithOrNull(
            compareBy(
                { it.parentLogicalMinZoom >= CameraRuntimeCapabilities.DEFAULT_ZOOM },
                { it.isPhysicalSubLens },
            ),
        )

/**
 * S1479: the index the session can actually bind in the current mode. The video pipeline is built
 * through `VideoCapture.withOutput`, which carries no physical camera id, so a sub-lens selection
 * silently binds its logical parent's optics - the lens label and the zoom multiplier must describe
 * that parent instead of the sub-lens the user picked in photo mode. Identity in photo mode, for a
 * logical entry, and when the parent is absent from [this] (it never is - the enumeration keeps
 * every logical camera - but an unchanged index degrades to today's behaviour rather than crashing).
 * Lives outside the session class because it is a pure question about a list, and the class sits on
 * detekt's LargeClass and TooManyFunctions ceilings.
 */
internal fun List<CameraLensEntry>.bindableIndex(index: Int, videoMode: Boolean): Int {
    val subLens = getOrNull(index)?.takeIf { videoMode && it.isPhysicalSubLens } ?: return index
    val parent = indexOfFirst { it.logicalCameraId == subLens.logicalCameraId && !it.isPhysicalSubLens }
    return if (parent >= 0) parent else index
}

/**
 * S1189: true when [lens] is the widest of several lenses facing the same way. A device with a
 * single lens on that side gets false, so its only camera keeps reading as the plain wide lens
 * instead of being relabelled "ultra-wide" by having nothing to compare against.
 */
private fun isWidestOfFacing(lens: CameraLensEntry, lenses: List<CameraLensEntry>): Boolean {
    val sameFacing = lenses.filter { it.lensFacing == lens.lensFacing && it.focalLengthMm > 0f }
    if (sameFacing.size < 2 || lens.focalLengthMm <= 0f) return false
    return sameFacing.none { it.focalLengthMm < lens.focalLengthMm }
}

/**
 * S1181: reports an errored finalize honestly and leaves nothing broken behind.
 *
 * Leaving the camera screen mid-recording is a user action, not a fault: CameraX surfaces it as
 * NO_VALID_DATA / SOURCE_INACTIVE, so those stay at info level while genuine faults (storage, encoder,
 * recorder, output options) keep the error level they need to be noticed at. The output file is dropped
 * only when it holds nothing playable - errors that still yield a valid truncated file (size, duration
 * and storage limits) keep theirs, as CameraX documents those as usable.
 *
 * Lives outside the session class because the decision is a pure function of the finalize outcome and
 * the file - it reads no session state.
 */
private fun handleFinalizeError(outputFile: File, error: Int) {
    val cancelled = error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ||
        error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
    if (cancelled) {
        Timber.i("CameraCaptureSessionManager: recording cancelled before any valid data ($error)")
    } else {
        Timber.e("CameraCaptureSessionManager: recording finalize error $error")
    }
    val unusable = error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ||
        !outputFile.exists() || outputFile.length() == 0L
    if (!unusable) return
    if (outputFile.exists() && !outputFile.delete()) {
        Timber.w("CameraCaptureSessionManager: could not delete unusable recording ${outputFile.name}")
    }
}

/**
 * S1457: returns a freshly bound camera to [ratio], which a rebind rebuilt at the lens default.
 *
 * Clamped rather than dropped, because a rebind can narrow the zoom range - an extension session
 * does - and the nearest reachable ratio stays closer to what the viewfinder showed than 1x would.
 * Lives outside the session class for the reason [restoreExif] does: it reads no session state.
 */
private fun Camera.restoreZoomRatio(ratio: Float) {
    val state = cameraInfo.zoomState.value
    val clamped = state?.let { ratio.coerceIn(it.minZoomRatio, it.maxZoomRatio) } ?: ratio
    runCatching { cameraControl.setZoomRatio(clamped) }
        .onFailure { Timber.w(it, "CameraCaptureSessionManager: zoom restore failed") }
}

/**
 * S1262: which of the three extension-backed intents the lens behind [baseSelector] actually offers.
 * All three are photo-only, so video mode offers none of them.
 *
 * Lives outside the session class for the reason [restoreExif] does: it reads no session state.
 */
private fun ExtensionsManager?.offeredExtensions(
    baseSelector: CameraSelector,
    videoMode: Boolean,
): CameraExtensionSelector.Intents {
    return CameraExtensionSelector.Intents(
        hdr = !videoMode && this?.isExtensionAvailable(baseSelector, ExtensionMode.HDR) == true,
        night = !videoMode && this?.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) == true,
        bokeh = !videoMode && this?.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH) == true,
    )
}
