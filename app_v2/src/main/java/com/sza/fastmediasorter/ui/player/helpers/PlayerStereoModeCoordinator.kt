package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideEntity
import com.sza.fastmediasorter.domain.model.StereoMode
import kotlinx.coroutines.CoroutineDispatcher
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
 *   - `stereoMode` - effective mode driving rendering; combines auto-detection, per-file
 *     override, and the detected mode.
 *   - `detectedStereoMode` - last value reported by the auto-detector; persisted so the
 *     dialog can return to AUTO without re-running detection.
 *
 * The coordinator is created inside PlayerViewModel's constructor and uses `viewModelScope`
 * for its IO work (per-file override load/save). All state is `@Volatile` where it can be
 * observed from the main thread mid-coroutine.
 */
class PlayerStereoModeCoordinator(
    private val stereoFormatOverrideDao: StereoFormatOverrideDao,
    private val scope: CoroutineScope,
    private val getCurrentFilePath: () -> String?,
    // S0326: global default stereo mode applied only when detection yields no result and no
    // per-file override exists. Defaults to MONO (plain 2D) for call sites that do not supply it.
    private val getGlobalDefaultStereoMode: () -> StereoMode = { StereoMode.MONO },
    // Injected so per-file override load/save is deterministic in unit tests.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Requested stereo mode - represents user/detector intent before pipeline application.
     * May carry [StereoMode.AUTO] (let detector decide) or [StereoMode.UNKNOWN] (sentinel,
     * detector ran but produced no conclusive result). Not safe to feed into renderer/decoder.
     */
    private val _requestedStereoMode = MutableStateFlow(StereoMode.AUTO)
    val requestedStereoMode: StateFlow<StereoMode> = _requestedStereoMode.asStateFlow()

    /**
     * Effective stereo mode - the value actually applied to renderer/decoder pipeline.
     * Initialised to [StereoMode.MONO] and never moves to [StereoMode.AUTO] or
     * [StereoMode.UNKNOWN]; if a transition would resolve to either, the prior known
     * effective is kept until detection produces a concrete value.
     */
    private val _effectiveStereoMode = MutableStateFlow(StereoMode.MONO)
    val effectiveStereoMode: StateFlow<StereoMode> = _effectiveStereoMode.asStateFlow()

    /** Backwards-compatible alias preserved for existing call sites. */
    val stereoMode: StateFlow<StereoMode> = effectiveStereoMode

    private val _detectedStereoMode = MutableStateFlow(StereoMode.UNKNOWN)
    val detectedStereoMode: StateFlow<StereoMode> = _detectedStereoMode.asStateFlow()

    @Volatile
    private var hasManualStereoSelection = false

    @Volatile
    private var ignoreForcedFormatForCurrentFile = false

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

        publishEffective(resolvedMode, "manual-set", requested = mode)
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
            resolveStereoMode(mode)
        }

        publishEffective(resolvedMode, "auto-detect", requested = mode)
    }

    /**
     * Reset stereo mode when navigating to a new file so auto-detection starts fresh,
     * while per-file override may still seed the initial effective mode.
     */
    fun resetStereoModeForNewFile(filePath: String?) {
        hasManualStereoSelection = false
        ignoreForcedFormatForCurrentFile = false
        _detectedStereoMode.value = StereoMode.UNKNOWN
        currentStereoOverridePath = filePath
        currentStereoOverrideMode = null
        publishEffective(resolveStereoMode(StereoMode.AUTO), "reset-new-file", requested = StereoMode.AUTO)

        if (filePath.isNullOrBlank()) return

        scope.launch(ioDispatcher) {
            val rememberedMode = stereoFormatOverrideDao.getEntry(filePath)?.let { entry ->
                StereoMode.fromKey(entry.stereoModeKey)
            }?.takeUnless { it == StereoMode.UNKNOWN || it == StereoMode.AUTO }

            withContext(Dispatchers.Main) {
                if (currentStereoOverridePath != filePath) return@withContext
                currentStereoOverrideMode = rememberedMode

                if (!hasManualStereoSelection && !ignoreForcedFormatForCurrentFile) {
                    val resolvedMode = resolveStereoMode(_detectedStereoMode.value)
                    publishEffective(resolvedMode, "remembered-per-file", requested = StereoMode.AUTO)
                }
            }
        }
    }

    fun rememberStereoModeForCurrentFile(mode: StereoMode) {
        val filePath = getCurrentFilePath() ?: return
        currentStereoOverridePath = filePath

        scope.launch(ioDispatcher) {
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
     * Single sink for transitioning the requested + effective stereo mode pair.
     * The effective flow never accepts [StereoMode.AUTO] or [StereoMode.UNKNOWN]: such transitions
     * are suppressed (logged once, prior effective value preserved) so downstream consumers
     * (decoder, renderer) never observe sentinel values that would force a MONO fallback.
     */
    private fun publishEffective(mode: StereoMode, reason: String, requested: StereoMode) {
        _requestedStereoMode.value = requested
        if (mode == StereoMode.AUTO || mode == StereoMode.UNKNOWN) {
            Timber.d(
                "PlayerStereoModeCoordinator: suppressed effective=%s reason=%s (kept=%s)",
                mode,
                reason,
                _effectiveStereoMode.value,
            )
            return
        }
        if (_effectiveStereoMode.value == mode) return
        Timber.d(
            "PlayerStereoModeCoordinator: effective=%s reason=%s (requested=%s)",
            mode,
            reason,
            requested,
        )
        _effectiveStereoMode.value = mode
    }

    private fun resolveAutoStereoMode(): StereoMode {
        val detected = _detectedStereoMode.value
        // Per-file override is intentionally ignored here (user picked AUTO to drop it); fall back
        // to the global default when detection produced nothing.
        if (detected == StereoMode.UNKNOWN) return globalDefaultOrAuto()
        return if (ignoreForcedFormatForCurrentFile) detected else resolveStereoMode(detected)
    }

    /**
     * Resolution chain (S0326): per-file override > positive detection > global default > AUTO.
     * The global default is consulted only when no override exists and detection is inconclusive.
     */
    private fun resolveStereoMode(detected: StereoMode): StereoMode {
        currentStereoOverrideMode
            ?.takeUnless { it == StereoMode.AUTO || it == StereoMode.UNKNOWN }
            ?.let { return it }
        if (detected != StereoMode.AUTO && detected != StereoMode.UNKNOWN) return detected
        return globalDefaultOrAuto()
    }

    /** Global default unless it is itself a sentinel, in which case AUTO (suppressed downstream). */
    private fun globalDefaultOrAuto(): StereoMode {
        Timber.d("S0326: global default fallback slot")
        return getGlobalDefaultStereoMode().takeUnless { it == StereoMode.AUTO || it == StereoMode.UNKNOWN }
            ?: StereoMode.AUTO
    }
}
