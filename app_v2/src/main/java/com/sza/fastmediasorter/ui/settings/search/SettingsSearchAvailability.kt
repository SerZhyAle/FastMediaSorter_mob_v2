package com.sza.fastmediasorter.ui.settings.search

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
 * No `BuildConfig.*` flag is read in this class (CLAUDE.md Rule 15 conformance). The
 * flavor-specific contributions are the only source of truth.
 */
@Singleton
class SettingsSearchAvailability @Inject constructor(
    @SupportedMediaSection private val supportedMedia: Set<@JvmSuppressWildcards String>
) {

    fun isAvailable(sectionId: String): Boolean = when (sectionId) {
        "images", "video", "audio", "documents" -> sectionId in supportedMedia
        else -> true
    }
}
