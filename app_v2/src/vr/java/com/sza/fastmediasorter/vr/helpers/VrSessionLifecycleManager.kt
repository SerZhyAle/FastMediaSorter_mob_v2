package com.sza.fastmediasorter.vr.helpers

import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.player.VrForcedFormatResolver
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import com.sza.fastmediasorter.ui.player.render.RenderTarget
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrLayerFactory
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrRenderContext
import com.sza.fastmediasorter.ui.player.render.stereoscopic.VrRenderingMode
import com.sza.fastmediasorter.vr.VrPlayerActivity
import com.sza.fastmediasorter.vr.VrLaunchRoute
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import com.sza.fastmediasorter.vr.render.VrPhotoSphereRenderer
import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/** Owns immersive session lifecycle: route decisions, fallbacks, mode switches, layer descriptor refresh. Decomposed per S0033 Phase 05. */
internal class VrSessionLifecycleManager(
    private val activity: VrPlayerActivity,
    private val xrSessionManagerProvider: () -> OpenXrSessionManager?,
    private val stereoDetector: StereoDetector,
    private val routeDecisionHelper: VrRouteDecisionHelper,
    private val playbackPrefs: SharedPreferences,
    private val vrLayerFactory: VrLayerFactory,
    private val onCriticalLifecycleEvent: (String) -> Unit,
) {

    @Volatile
    var xrInitializationRequested: Boolean = false

    @Volatile
    var xrInitStartedAtMs: Long = 0L

    @Volatile
    var standardPlayerFallbackLaunched: Boolean = false

    var currentStereoMode: StereoMode = StereoMode.MONO
    var currentRenderingMode: VrRenderingMode = VrRenderingMode.CINEMA

    // S0026: hint from BrowseEventHandler — single-use, consumed by first route resolution.
    var initialDetectedStereoModeHint: StereoMode? = null

    @Volatile
    var currentVrImageKey: String? = null

    private var playbackRouteJob: Job? = null

    fun cancelRouteJob() {
        playbackRouteJob?.cancel()
        playbackRouteJob = null
    }

    fun resolvePlaybackRoute(reason: String) {
        if (standardPlayerFallbackLaunched || activity.isFinishing || activity.isDestroyed) return

        val currentFile = activity.viewModelInternal.state.value.currentFile ?: run {
            Timber.d("VrPlayerActivity: resolvePlaybackRoute(%s) postponed — currentFile not ready", reason)
            return
        }

        playbackRouteJob?.cancel()
        playbackRouteJob = activity.lifecycleScope.launch {
            val routeDecision = buildRouteDecision(currentFile, activity.viewModelInternal.stereoMode.value)
            if (standardPlayerFallbackLaunched || activity.isFinishing || activity.isDestroyed) return@launch

            // S0018 defensive guard: panel-only reasons MUST yield STANDARD_PANEL_FALLBACK.
            // If the helper produced a contradictory pair, force panel fallback rather than
            // entering immersive — matches the unit-test invariant in VrRouteDecisionHelperTest.
            val panelOnlyReasons = setOf(
                "auto-immersive-disabled",
                "user-forced-panel",
                "disable-3d-vr",
                "plain-2d-content",
            )
            if (routeDecision.logReason in panelOnlyReasons &&
                routeDecision.route != VrLaunchRoute.STANDARD_PANEL_FALLBACK) {
                // recovery: not a route decision emission
                Timber.e(
                    "VrPlayerActivity: (route, reason) invariant violated — route=%s reason=%s; forcing panel fallback",
                    routeDecision.route,
                    routeDecision.logReason,
                )
                launchStandardPlayerFallback(currentFile, "invariant-violation:${routeDecision.logReason}")
                return@launch
            }

            applyStereoModeToVrRenderers(routeDecision.effectiveStereoMode, "route-decision")

            when (routeDecision.route) {
                VrLaunchRoute.STANDARD_PANEL_FALLBACK -> {
                    launchStandardPlayerFallback(currentFile, reason)
                }

                VrLaunchRoute.CINEMA_IMMERSIVE,
                VrLaunchRoute.IMMERSIVE_VIDEO,
                VrLaunchRoute.IMMERSIVE_STATIC_IMAGE -> {
                    if (!xrInitializationRequested) {
                        startXrInitialization(reason, routeDecision)
                    }
                }

                VrLaunchRoute.UNSUPPORTED_IMMERSIVE_WITH_MESSAGE -> {
                    launchUnsupportedImmersiveFallback(currentFile, reason, routeDecision)
                }
            }
        }
    }

    private suspend fun buildRouteDecision(
        currentFile: MediaFile,
        requestedStereoMode: StereoMode,
    ): VrRouteDecision {
        val settings = activity.viewModelInternal.getSettings()
        val effectiveMode = resolveLaunchStereoMode(currentFile, requestedStereoMode, settings.vrAutoDetectFormat)
        val routeDecision = routeDecisionHelper.decide(
            currentFile,
            effectiveMode,
            settings,
            activity.forceImmersiveThisLaunchInternal,
            activity.forcePanelThisLaunchInternal,
        )
        routeDecision.logTo(currentFile, requestedStereoMode, settings.vrAutoDetectFormat)
        return routeDecision
    }

    private suspend fun resolveLaunchStereoMode(
        currentFile: MediaFile,
        requestedStereoMode: StereoMode,
        autoDetectEnabled: Boolean,
    ): StereoMode {
        // S0026: when Browse provided a detected mode hint, honor it over the coordinator's
        // current value if the coordinator is still at the MONO/UNKNOWN/AUTO default. The hint
        // is single-use; subsequent file changes within the same VrPlayerActivity instance
        // re-detect normally via the stereoDetector block below.
        val hintToUse = initialDetectedStereoModeHint
        initialDetectedStereoModeHint = null

        if (hintToUse != null && hintToUse != StereoMode.UNKNOWN && hintToUse != StereoMode.MONO &&
            (requestedStereoMode == StereoMode.MONO ||
                requestedStereoMode == StereoMode.AUTO ||
                requestedStereoMode == StereoMode.UNKNOWN)
        ) {
            Timber.i(
                "VrPlayerActivity: resolveLaunchStereoMode using browse hint=%s (was requested=%s)",
                hintToUse,
                requestedStereoMode,
            )
            return hintToUse
        }

        if (requestedStereoMode != StereoMode.AUTO && requestedStereoMode != StereoMode.UNKNOWN) {
            return requestedStereoMode
        }

        val detectedByFilename = stereoDetector.detectFromFilename(currentFile.path)
        val detectedMode = if (detectedByFilename != StereoMode.UNKNOWN) {
            detectedByFilename
        } else if (currentFile.width != null && currentFile.height != null) {
            stereoDetector.detectFromDimensions(currentFile.width, currentFile.height)
        } else {
            StereoMode.UNKNOWN
        }

        if (!autoDetectEnabled && !detectedMode.isSpherical()) {
            return StereoMode.MONO
        }

        val settings = activity.viewModelInternal.getSettings()
        return VrForcedFormatResolver.resolve(
            detected = detectedMode,
            perFileOverride = null,
            forcedPlat = VrForcedFormatResolver.mapPlatSetting(settings.vrForcedPlatFormat),
            forcedSpherical = VrForcedFormatResolver.mapSphericalSetting(settings.vrForcedSphericalFormat),
        )
    }

    fun launchStandardPlayerFallback(currentFile: MediaFile, reason: String) {
        if (standardPlayerFallbackLaunched || activity.isFinishing || activity.isDestroyed) return

        standardPlayerFallbackLaunched = true
        playbackRouteJob?.cancel()
        playbackRouteJob = null
        Timber.i("VrPlayerActivity: launching standard PlayerActivity fallback file=%s type=%s reason=%s",
            currentFile.path, currentFile.type, reason)
        Timber.d("VR_AUDIT/5: STANDARD_PANEL_FALLBACK from VR file=%s reason=%s — flicker source",
            currentFile.path, reason)
        forceStopVrPlayback("standard-player-fallback:$reason")
        activity.startActivity(Intent(activity.intent).apply {
            setClass(activity, PlayerActivity::class.java)
        })
        activity.finish()
    }

    fun launchUnsupportedImmersiveFallback(
        currentFile: MediaFile,
        reason: String,
        routeDecision: VrRouteDecision,
    ) {
        if (standardPlayerFallbackLaunched || activity.isFinishing || activity.isDestroyed) return

        standardPlayerFallbackLaunched = true
        playbackRouteJob?.cancel()
        playbackRouteJob = null
        Timber.w("VrPlayerActivity: unsupported immersive route file=%s type=%s stereo=%s reason=%s",
            currentFile.path, currentFile.type, routeDecision.effectiveStereoMode, reason)
        forceStopVrPlayback("unsupported-immersive:$reason")
        activity.showErrorInternal(activity.getString(routeDecision.userMessageResId ?: R.string.vr_immersive_unsupported_media))

        activity.window.decorView.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            activity.startActivity(Intent(activity.intent).apply {
                setClass(activity, PlayerActivity::class.java)
            })
            activity.finish()
        }, VrPlayerActivity.VR_FALLBACK_ERROR_DELAY_MS)
    }

    fun startXrInitialization(reason: String, routeDecision: VrRouteDecision) {
        xrInitializationRequested = true
        xrInitStartedAtMs = SystemClock.uptimeMillis()
        Timber.i("VrPlayerActivity: starting XR init (reason=%s route=%s)", reason, routeDecision.route)
        Timber.i("VR_PERF: [main] xr_init_requested  t=%d", xrInitStartedAtMs)
        Timber.d("VR_AUDIT/14: cold-start phase=startXrInitialization reason=%s route=%s tUptimeMs=%d",
            reason, routeDecision.route, xrInitStartedAtMs)
        activity.lifecycleScope.launch(Dispatchers.IO) {
            Timber.d("VrPlayerActivity: [IO] xrSessionManager.initialize starting")
            val ok = xrSessionManagerProvider()?.initialize(activity) ?: false
            Timber.i("VrPlayerActivity: [IO] xrSessionManager.initialize returned ok=%b  isFinishing=%b",
                ok, activity.isFinishing)
            if (!ok && !activity.isFinishing) {
                Timber.w("VrPlayerActivity: XR session could not start — falling back to flat cinema mode")
                fallbackToFlatCinemaMode("xr-session-init-failed")
            }
        }
    }

    // After XR-init failure: VrPlayerActivity at headset panel resolution (~4128x2208) makes
    // controls unreachable. Hand off to PlayerActivity so the Quest compositor renders a
    // usable 2D window; ResumeStateRepository restores the playback position.
    fun fallbackToFlatCinemaMode(reason: String) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            Timber.w("VrPlayerActivity: fallbackToFlatCinemaMode (reason=%s) — XR unavailable, routing to standard PlayerActivity",
                reason)
            xrInitializationRequested = false
            try {
                xrSessionManagerProvider()?.release()
            } catch (t: Throwable) {
                Timber.w(t, "VrPlayerActivity: fallbackToFlatCinemaMode — XR release failed, ignoring")
            }
            activity.showErrorInternal(activity.getString(R.string.vr_xr_fallback_flat_mode))
            onCriticalLifecycleEvent("fallback-flat-cinema:$reason")

            val currentFile = activity.viewModelInternal.state.value.currentFile
            if (currentFile == null) {
                Timber.w("VrPlayerActivity: fallbackToFlatCinemaMode — currentFile is null, finishing without fallback target")
                activity.finish()
                return@runOnUiThread
            }
            activity.window.decorView.postDelayed({
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                launchStandardPlayerFallback(currentFile, "xr-init-failed:$reason")
            }, VrPlayerActivity.VR_FALLBACK_ERROR_DELAY_MS)
        }
    }

    fun forceStopVrPlayback(reason: String) {
        Timber.w("VrPlayerActivity: forceStopVrPlayback reason=%s", reason)

        val pipeline = activity.vrRenderPipelineManagerInternal
        pipeline?.pendingVrBridgeSurface = null
        pipeline?.pendingVrBridgeTextureId = -1
        pipeline?.setVrRenderingActive(false, "force-stop:$reason")

        try {
            activity.lifecycleManagerInternal.saveCurrentPlaybackPosition()
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: forceStopVrPlayback — saveCurrentPlaybackPosition failed")
        }

        try {
            activity.clearPlaybackVideoSurfaceInternal()
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: forceStopVrPlayback — clearing video surface failed")
        }

        try {
            activity.stopVideoPlaybackInternal()
        } catch (t: Throwable) {
            Timber.e(t, "VrPlayerActivity: forceStopVrPlayback — stopVideoPlayback failed")
        }

        try {
            xrSessionManagerProvider()?.release()
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: forceStopVrPlayback — XR release failed")
        }
    }

    fun exitVrAndStopPlayback(reason: String) {
        forceStopVrPlayback(reason)
        val state = activity.viewModelInternal.state.value
        // S0038 — createPanelIntent targets PlayerActivity directly, bypassing the VR-flavor
        // PLAYER_ACTIVITY_CLASS override (eliminates two-hop relaunch / clone windows).
        val playerIntent = PlayerActivity.createPanelIntent(
            context = activity, resourceId = state.resourceId, skipAvailabilityCheck = true,
            initialFilePath = state.currentFile?.path, isPlaying = false,
            isSlideshowEnabled = state.isSlideshowEnabled)
        VrTaskTransition.exitImmersiveToFlatPlayer(activity, playerIntent)
    }

    fun launchVrFailureRecovery(
        userMessage: String,
        reason: String,
        shouldFinish: Boolean,
    ) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                Timber.w("VrPlayerActivity: skip VR failure recovery — activity is finishing/destroyed (%s)", reason)
                return@runOnUiThread
            }
            Timber.e("VrPlayerActivity: launchVrFailureRecovery reason=%s shouldFinish=%b", reason, shouldFinish)
            forceStopVrPlayback(reason)
            activity.showErrorInternal(userMessage)
            if (shouldFinish) {
                activity.window.decorView.post { VrTaskTransition.exitImmersiveToPanel(activity) }
            }
        }
    }

    fun switchToPanelPreservingPosition() {
        if (activity.isFinishing || activity.isDestroyed) return
        Timber.i("VrPlayerActivity: switchToPanelPreservingPosition")
        forceStopVrPlayback("toggle-to-panel")
        activity.startActivity(Intent(activity.intent).apply {
            setClass(activity, VrPlayerActivity::class.java)
            putExtra(VrPlayerActivity.EXTRA_FORCE_IMMERSIVE, false)
            // WHY: EXTRA_FORCE_PANEL=true tells VrRouteDecisionHelper to return STANDARD_PANEL_FALLBACK
            // unconditionally, even for spherical/SBS content that would otherwise be re-routed
            // back to immersive. Without this flag the toggle had no effect on VR180/360 files.
            putExtra(VrPlayerActivity.EXTRA_FORCE_PANEL, true)
        })
        activity.finish()
    }

    fun switchToImmersivePreservingPosition() {
        if (activity.isFinishing || activity.isDestroyed) return
        Timber.i("VrPlayerActivity: switchToImmersivePreservingPosition")
        forceStopVrPlayback("toggle-to-immersive")
        activity.startActivity(Intent(activity.intent).apply {
            putExtra(VrPlayerActivity.EXTRA_FORCE_IMMERSIVE, true)
        })
        activity.finish()
    }

    fun applyStereoModeToVrRenderers(mode: StereoMode, reason: String) {
        if (currentStereoMode == mode) return

        currentStereoMode = mode
        activity.vrRenderPipelineManagerInternal?.stereoRenderer?.setStereoMode(mode)
        activity.vrRenderPipelineManagerInternal?.photoSphereRenderer?.setStereoMode(mode)
        refreshLayerDescriptor(reason)
        val label = mode.toHudLabel()
        activity.vrRenderPipelineManagerInternal?.vrHudManager?.showStereoModeLabel(label)
        // S0019: passive only — interactivity in S0024
        if (label != null && reason == "route-decision") {
            activity.vrRenderPipelineManagerInternal?.vrHudManager?.showBannerText(
                activity.getString(R.string.vr_hud_applied_format, label)
            )
        }
    }

    private fun StereoMode.toHudLabel(): String? = when (this) {
        StereoMode.MONO             -> "2D"
        StereoMode.SBS_FULL         -> "3D SBS"
        StereoMode.SBS_HALF         -> "3D SBS½"
        StereoMode.OU               -> "3D O/U"
        StereoMode.EQUIRECT_360_MONO -> "360° Mono"
        StereoMode.EQUIRECT_360_SBS  -> "360° SBS"
        StereoMode.EQUIRECT_360_OU   -> "360° O/U"
        StereoMode.EQUIRECT_180_MONO -> "180° Mono"
        StereoMode.EQUIRECT_180_SBS  -> "180° SBS"
        StereoMode.VR180_FISHEYE_SBS -> "VR180°"
        StereoMode.CYLINDER_180      -> "Cylinder"
        StereoMode.AUTO, StereoMode.UNKNOWN -> null
    }

    fun refreshLayerDescriptor(reason: String) {
        val descriptor = vrLayerFactory.describe(currentStereoMode, currentRenderingMode)
        val pipeline = activity.vrRenderPipelineManagerInternal
        pipeline?.currentLayerDescriptor = descriptor
        if (pipeline != null) {
            pipeline.cachedDescriptorSourceAspectRatio = if (descriptor.heightMeters > 0f) {
                descriptor.widthMeters / descriptor.heightMeters
            } else {
                VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO
            }
        }
        val zoom = activity.vrZoomManagerInternal
        if (zoom != null) {
            zoom.setBaseDescriptor(descriptor)
        } else {
            xrSessionManagerProvider()?.updateLayerDescriptor(descriptor, reason)
        }
        Timber.d(
            "VrPlayerActivity: layer descriptor → %s (reason=%s, stereo=%s, renderMode=%s)",
            descriptor.type,
            reason,
            currentStereoMode,
            currentRenderingMode,
        )
        assertStereoCoherence(reason)
    }

    // Coherence guard — compares currentStereoMode against coordinator's stereoMode on each descriptor refresh.
    private fun assertStereoCoherence(reason: String) {
        val coordinatorEffective = activity.viewModelInternal.stereoMode.value
        if (coordinatorEffective != currentStereoMode) {
            Timber.w(
                "VrPlayerActivity: stereo coherence MISMATCH coordinator=%s activity=%s descriptor=%s reason=%s",
                coordinatorEffective,
                currentStereoMode,
                activity.vrRenderPipelineManagerInternal?.currentLayerDescriptor?.type,
                reason,
            )
        } else {
            Timber.d(
                "VrPlayerActivity: stereo coherence OK %s reason=%s",
                coordinatorEffective,
                reason,
            )
        }
    }

    suspend fun syncVrImageTarget(
        currentFile: MediaFile?,
        renderer: VrPhotoSphereRenderer,
    ) {
        if (currentFile?.type != MediaType.IMAGE) {
            currentVrImageKey = null
            return
        }

        val detected = stereoDetector.detectForImage(
            path = currentFile.path,
            width = currentFile.width,
            height = currentFile.height,
        )
        activity.viewModelInternal.setAutoDetectedStereoMode(detected)

        val imageKey = buildVrImageKey(currentFile)
        if (currentVrImageKey == imageKey) return
        currentVrImageKey = imageKey

        renderer.render(
            RenderTarget(
                mediaFile = currentFile,
                path = currentFile.path,
                priority = RenderPriority.NEXT,
            )
        )
    }

    fun isVrStaticImageActive(): Boolean {
        val currentFile = activity.viewModelInternal.state.value.currentFile ?: return false
        return currentFile.type == MediaType.IMAGE &&
            activity.vrRenderPipelineManagerInternal?.photoSphereRenderer?.hasRenderableTexture() == true
    }

    fun isCurrentVrStaticImageSession(): Boolean {
        val currentFile = activity.viewModelInternal.state.value.currentFile ?: return false
        return currentFile.type == MediaType.IMAGE &&
            (currentStereoMode.isSpherical() || currentStereoMode.isStereoscopic())
    }

    fun buildVrImageKey(file: MediaFile): String = listOf(
        file.path,
        file.size,
        file.lastModified,
        file.width,
        file.height,
    ).joinToString("|")

    fun applyRenderingModeFromPrefs() {
        currentRenderingMode = VrRenderingMode.fromPreferenceValue(
            playbackPrefs.getString(PlaybackControlPreferences.KEY_VR_RENDERING_MODE, "CINEMA"))
    }
}
