package com.sza.fastmediasorter.domain.model

/**
 * S3222: everything the next broadcast session is configured with, held by [AppSettings] as one nested
 * field.
 *
 * They live here rather than inline in [AppSettings] because a JVM method descriptor may carry at most
 * 255 slots including `this`, and Kotlin's synthetic default-argument constructor spends one slot per
 * parameter plus one bitmask int per 32 parameters plus a marker. With these fourteen inline the
 * descriptor reached 257 slots, which kotlinc emits without complaint and ART rejects at
 * class-verification time - every `AppSettings()` and every `AppSettings.copy(..)` in the process.
 * Grouping a domain into a nested class costs one slot instead of one per field; [LauncherSettings] was
 * the first of these (S2300).
 *
 * The group property in [AppSettings] is called `broadcast` because that is the flat prefix these names
 * carried, and `check_device_profile_presets.ps1` expands `broadcast` + `port` back into the
 * `broadcastPort` that the preset CSV and the non-presettable registry still spell (S1470). The same
 * convention is why this file sits directly in `domain/model/` rather than in a `broadcast/` subpackage:
 * the expander looks for the group's file beside `AppSettings.kt`.
 */
data class BroadcastSettings(
    // S2817: an absent preference preserves the broadcast session defaults used before settings existed.
    val streamTitle: String = DEFAULT_BROADCAST_STREAM_TITLE,
    val bitRateBps: Int = 128_000,
    val port: Int = 8768,
    val sampleRateHz: Int = 44_100,
    val channelCount: Int = 1,
    val autoOpenShare: Boolean = true,
    // S2814: stable identity of this phone as a broadcast source. Null until the first broadcast
    // generates a UUID and persists it; a receiver that scanned this phone before recognises it
    // across address changes instead of adding a second catalog entry.
    val sourceDeviceId: String? = null,
    // S3038: camera and microphone defaults for broadcast mode selection, plus video quality.
    val cameraEnabled: Boolean = false,
    val microphoneEnabled: Boolean = true,
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val videoFps: Int = 30,
    val videoBitrateBps: Int = 2_000_000,
    // S3049: digital PCM microphone gain percentage for broadcasts (50% - 400%, default 100%).
    val micGainPercent: Int = 100,
)
