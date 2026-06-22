package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifier for the multibound set of supported media section ids contributed by
 * per-flavor modules. Empty when no media is supported (theoretical - every shipping
 * flavor contributes at least one media id today).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SupportedMediaSection

/**
 * Decides whether a `SettingsSearchIndex` entry should appear in search results for the
 * current build. Non-media sections (`general`, `playback`, `destinations`, `media`, `other`)
 * are always available. Media sections (`images`, `video`, `audio`, `documents`) are
 * available iff the current flavor contributes the matching id to `supportedMedia` -
 * see `Standard/NoLegal/Lite/Photos/Legacy/VrSettingsSearchAvailabilityModule.kt`.
 *
 * Some rows are additionally hidden at runtime by device traits (SDK level, sensors). Those
 * keys are filtered via `deviceFeatureGate` so search never shows dead results for rows the
 * current device cannot surface at all.
 *
 * No `BuildConfig.*` flag is read in this class (CLAUDE.md Rule 15 conformance). The
 * flavor-specific contributions are the only source of truth.
 */
@Singleton
class SettingsSearchAvailability @Inject constructor(
    @SupportedMediaSection private val supportedMedia: Set<@JvmSuppressWildcards String>,
    private val deviceFeatureGate: SettingsSearchDeviceFeatureGate
) {

    fun isAvailable(entry: SettingsSearchIndex): Boolean =
        isSectionAvailable(entry.sectionId) && deviceFeatureGate.isAvailable(entry.key)

    private fun isSectionAvailable(sectionId: String): Boolean = when (sectionId) {
        "images", "video", "audio", "documents" -> sectionId in supportedMedia
        else -> true
    }
}
