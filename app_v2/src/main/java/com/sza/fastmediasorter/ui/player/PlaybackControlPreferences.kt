package com.sza.fastmediasorter.ui.player

object PlaybackControlPreferences {
    const val PREFS_NAME = "playback_control_dialog"
    const val KEY_LAST_TAB = "last_tab"
    const val KEY_LAST_SECTION = "last_section"
    const val KEY_HUE_DEGREES = "hue_degrees"
    const val KEY_BRIGHTNESS_PERCENT = "brightness_percent"
    const val KEY_LAST_NON_ZERO_VOLUME = "last_non_zero_volume"
    // Speed set via the Control dialog. Stored separately so applyPlayerSettings() can restore it
    // after onPlaybackReady() - which always fires with the default 1.0x from PlayerSettingsDialog.
    const val KEY_SPEED = "playback_speed"
    const val KEY_PLAYBACK_ORDER_AUDIO = "playback_order_audio"
    const val KEY_PLAYBACK_ORDER_VIDEO = "playback_order_video"
    // S0241: legacy VR rendering-mode/IPD keys removed alongside the VR runtime.
}
