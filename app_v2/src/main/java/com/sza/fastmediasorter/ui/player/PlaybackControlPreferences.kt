package com.sza.fastmediasorter.ui.player

object PlaybackControlPreferences {
    const val PREFS_NAME = "playback_control_dialog"
    const val KEY_LAST_TAB = "last_tab"
    const val KEY_HUE_DEGREES = "hue_degrees"
    const val KEY_BRIGHTNESS_PERCENT = "brightness_percent"
    const val KEY_LAST_NON_ZERO_VOLUME = "last_non_zero_volume"
    // Speed set via the Control dialog. Stored separately so applyPlayerSettings() can restore it
    // after onPlaybackReady() — which always fires with the default 1.0x from PlayerSettingsDialog.
    const val KEY_SPEED = "playback_speed"
    // VR-only: rendering mode (CINEMA / FULL_STEREO) and IPD in mm (spec §5.8)
    const val KEY_VR_RENDERING_MODE = "vr_rendering_mode"
    const val KEY_VR_IPD_MM = "vr_ipd_mm"
}