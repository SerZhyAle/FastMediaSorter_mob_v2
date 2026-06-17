package com.sza.fastmediasorter.testutil

import com.sza.fastmediasorter.core.capability.MediaCapabilities

/**
 * Single test-side construction point for [MediaCapabilities] with all-true defaults. The production
 * data class deliberately has no defaults (so a missing field is a compile error in real code); this
 * factory keeps that strictness out of tests - a future field only needs one new defaulted parameter
 * here instead of an edit at every test site.
 */
fun testMediaCapabilities(
    supportsVideo: Boolean = true,
    supportsAudio: Boolean = true,
    supportsImages: Boolean = true,
    supportsDocuments: Boolean = true,
    supportsEpub: Boolean = true,
    supportsCloud: Boolean = true,
    supportsLocalNetworkSources: Boolean = true,
    supportsDefaultPlayer: Boolean = true,
    supportsCast: Boolean = true,
    supportsMicRecording: Boolean = true,
    supportsVrPlayer: Boolean = true,
    supportsWearCompanion: Boolean = true,
): MediaCapabilities = MediaCapabilities(
    supportsVideo = supportsVideo,
    supportsAudio = supportsAudio,
    supportsImages = supportsImages,
    supportsDocuments = supportsDocuments,
    supportsEpub = supportsEpub,
    supportsCloud = supportsCloud,
    supportsLocalNetworkSources = supportsLocalNetworkSources,
    supportsDefaultPlayer = supportsDefaultPlayer,
    supportsCast = supportsCast,
    supportsMicRecording = supportsMicRecording,
    supportsVrPlayer = supportsVrPlayer,
    supportsWearCompanion = supportsWearCompanion,
)
