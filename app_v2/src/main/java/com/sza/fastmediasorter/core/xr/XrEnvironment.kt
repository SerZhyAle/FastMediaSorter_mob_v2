package com.sza.fastmediasorter.core.xr

/**
 * Source of truth for the host XR runtime. Detection lives in [XrEnvironmentDetector].
 *
 * The enum is reachable from every flavor; only the [XrEnvironmentDetector] implementation
 * is flavor-specific.
 */
enum class XrEnvironment {
    /** Plain Android phone / tablet. No XR runtime present. */
    NONE,

    /** Meta Quest device on Horizon OS (Quest 2 / 3 / 3S / Pro). */
    VR_QUEST,

    /** Android XR device (or Android Studio Canary XR emulator). */
    ANDROID_XR,
}
