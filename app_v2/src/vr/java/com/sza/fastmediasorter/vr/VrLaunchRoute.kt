package com.sza.fastmediasorter.vr

/**
 * High-level playback route for the VR player host.
 *
 * The route is intentionally broader than MediaType because immersive support depends on both
 * the media family and the detected stereo/projection layout.
 */
internal enum class VrLaunchRoute {
    STANDARD_PANEL_FALLBACK,
    IMMERSIVE_VIDEO,
    IMMERSIVE_STATIC_IMAGE,
    UNSUPPORTED_IMMERSIVE_WITH_MESSAGE,
}