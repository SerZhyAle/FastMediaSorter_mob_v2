package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideEntity
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.VrForcedFormatResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Owns the stereo-mode resolution pipeline for the player:
 *   - `stereoMode` — effective mode driving rendering; combines auto-detection, per-file
 *     override, and the global VR forced-format settings.
 *   - `detectedStereoMode` — last value reported by the auto-detector; persisted so the
 *     dialog can return to AUTO without re-running detection.
 *
 * The coordinator is created inside PlayerViewModel's constructor and uses `viewModelScope`
 * for its IO work (per-file override load/save). All state is `@Volatile` where it can be
 * observed from the main thread mid-coroutine.
 */
class PlayerStereoModeCoordinator(
    private val stereoFormatOverrideDao: StereoFormatOverrideDao,
    private val scope: CoroutineScope,
    private val getCurrentFilePath: () -> String?
) {

    private val _stereoMode = MutableStateFlow(StereoMode.AUTO)
    val stereoMode: StateFlow<StereoMode> = _stereoMode.asStateFlow()

    private val _detectedStereoMode = MutableStateFlow(StereoMode.UNKNOWN)
    val detectedStereoMode: StateFlow<StereoMode> = _detectedStereoMode.asStateFlow()

    @Volatile
    private var hasManualStereoSelection = false

    @Volatile
    private var ignoreForcedFormatForCurrentFile = false

    @Volatile
    private var vrForcedPlatFormat: StereoMode? = null

    @Volatile
    private var vrForcedSphericalFormat: StereoMode? = null

    @Volatile
    var vrRememberFileFormatEnabled: Boolean = true
        private set

    @Volatile
    private var currentStereoOverridePath: String? = null

    @Volatile
    private var currentStereoOverrideMode: StereoMode? = null

    /**
     * Set the stereo display mode for the current video.
     * [StereoMode.AUTO] returns to detected mode for the current file and intentionally
     * ignores any remembered forced format until the next file is loaded.
     */
    fun setStereoMode(mode: StereoMode) {
        hasManualStereoSelection = mode != StereoMode.AUTO
        ignoreForcedFormatForCurrentFile = mode == StereoMode.AUTO

        val resolvedMode = if (mode == StereoMode.AUTO) {
            resolveAutoStereoMode()
        } else {
            mode
        }

        if (_stereoMode.value == resolvedMode) return
        Timber.d("PlayerStereoModeCoordinator: stereoMode -> $resolvedMode (requested=$mode)")
        _stereoMode.value = resolvedMode
    }

    /**
     * Set the stereo mode auto-detected from video metadata/dimensions.
     * Always updates the detected-mode cache so the dialog can return to AUTO immediately.
     * Only applies to the effective mode when the user has not picked a manual override.
     */
    fun setAutoDetectedStereoMode(mode: StereoMode, forFilePath: String = "") {
        if (mode == StereoMode.UNKNOWN || mode == StereoMode.AUTO) return
        if (forFilePath.isNotEmpty() && forFilePath != currentStereoOverridePath) {
            Timber.w(
                "PlayerStereoModeCoordinator: discarding stale detection mode=$mode " +
                    "for=$forFilePath current=$currentStereoOverridePath"
            )
            return
        }
        _detectedStereoMode.value = mode

        if (hasManualStereoSelection) return

        val resolvedMode = if (ignoreForcedFormatForCurrentFile) {
            mode
        } else {
            resolveForcedStereoMode(mode)
        }

        if (_stereoMode.value == resolvedMode) return
        Timber.d("PlayerStereoModeCoordinator: auto-detected stereoMode -> $resolvedMode (detected=$mode)")
        _stereoMode.value = resolvedMode
    }

    /**
     * Reset stereo mode when navigating to a new file so auto-detection starts fresh,
     * while remembered forced format may still seed the initial effective mode.
     */
    fun resetStereoModeForNewFile(filePath: String?) {
        hasManualStereoSelection = false
        ignoreForcedFormatForCurrentFile = false
        _detectedStereoMode.value = StereoMode.UNKNOWN
        currentStereoOverridePath = filePath
        currentStereoOverrideMode = null
        _stereoMode.value = resolveForcedStereoMode(StereoMode.AUTO)

        if (!vrRememberFileFormatEnabled || filePath.isNullOrBlank()) return

        scope.launch(Dispatchers.IO) {
            val rememberedMode = stereoFormatOverrideDao.getEntry(filePath)?.let { entry ->
                StereoMode.fromKey(entry.stereoModeKey)
            }?.takeUnless { it == StereoMode.UNKNOWN || it == StereoMode.AUTO }

            withContext(Dispatchers.Main) {
                if (currentStereoOverridePath != filePath) return@withContext
                currentStereoOverrideMode = rememberedMode

                if (!hasManualStereoSelection && !ignoreForcedFormatForCurrentFile) {
                    val resolvedMode = resolveForcedStereoMode(_detectedStereoMode.value)
                    if (_stereoMode.value != resolvedMode) {
                        Timber.d("PlayerStereoModeCoordinator: remembered per-file stereoMode -> $resolvedMode (path=$filePath)")
                        _stereoMode.value = resolvedMode
                    }
                }
            }
        }
    }

    fun rememberStereoModeIfEnabled(mode: StereoMode) {
        if (!vrRememberFileFormatEnabled) return

        val filePath = getCurrentFilePath() ?: return
        currentStereoOverridePath = filePath

        scope.launch(Dispatchers.IO) {
            if (mode == StereoMode.AUTO) {
                currentStereoOverrideMode = null
                stereoFormatOverrideDao.deleteEntry(filePath)
                return@launch
            }

            currentStereoOverrideMode = mode
            stereoFormatOverrideDao.upsert(
                StereoFormatOverrideEntity(
                    filePath = filePath,
                    stereoModeKey = mode.name,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * Apply the latest settings snapshot. Returns true if any value relevant to
     * the effective mode changed — caller should then re-resolve the mode if the user
     * hasn't picked a manual override.
     */
    fun applySettings(
        forcedPlatFormat: StereoMode?,
        forcedSphericalFormat: StereoMode?,
        rememberFileFormat: Boolean
    ): Boolean {
        val forcedFormatChanged =
            vrForcedPlatFormat != forcedPlatFormat ||
                vrForcedSphericalFormat != forcedSphericalFormat
        vrForcedPlatFormat = forcedPlatFormat
        vrForcedSphericalFormat = forcedSphericalFormat

        val rememberFormatChanged = vrRememberFileFormatEnabled != rememberFileFormat
        vrRememberFileFormatEnabled = rememberFileFormat

        if ((rememberFormatChanged || forcedFormatChanged) &&
            !hasManualStereoSelection &&
            !ignoreForcedFormatForCurrentFile
        ) {
            _stereoMode.value = resolveForcedStereoMode(_detectedStereoMode.value)
            return true
        }
        return false
    }

    private fun resolveAutoStereoMode(): StereoMode {
        val detected = _detectedStereoMode.value
        if (detected == StereoMode.UNKNOWN) return StereoMode.AUTO
        return if (ignoreForcedFormatForCurrentFile) detected else resolveForcedStereoMode(detected)
    }

    private fun resolveForcedStereoMode(detected: StereoMode): StereoMode {
        val perFileOverride = if (vrRememberFileFormatEnabled) currentStereoOverrideMode else null
        return VrForcedFormatResolver.resolve(
            detected = detected,
            perFileOverride = perFileOverride,
            forcedPlat = vrForcedPlatFormat,
            forcedSpherical = vrForcedSphericalFormat,
        )
    }
}
