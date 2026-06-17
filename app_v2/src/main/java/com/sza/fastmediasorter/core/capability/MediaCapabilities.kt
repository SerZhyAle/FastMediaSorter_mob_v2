package com.sza.fastmediasorter.core.capability

/**
 * Flavor-resolved media and storage capability flags.
 *
 * This is the single typed surface that shared code injects instead of reading
 * flavor build flags directly (CLAUDE.md Rule 15 - flavor isolation). The
 * concrete values are bound per flavor by a
 * `MediaCapabilitiesModule` placed in each flavor source set
 * (`src/<flavor>/java/.../di/`), where reading that flavor's `BuildConfig` is
 * permitted. The flavor matrix in `app_v2/build.gradle.kts` stays the single
 * source of truth for the values.
 */
data class MediaCapabilities(
    val supportsVideo: Boolean,
    val supportsAudio: Boolean,
    val supportsImages: Boolean,
    val supportsDocuments: Boolean,
    val supportsEpub: Boolean,
    val supportsCloud: Boolean,
    val supportsLocalNetworkSources: Boolean,
    val supportsDefaultPlayer: Boolean,
    val supportsCast: Boolean,
    val supportsMicRecording: Boolean,
    val supportsVrPlayer: Boolean,
    val supportsWearCompanion: Boolean,
)
