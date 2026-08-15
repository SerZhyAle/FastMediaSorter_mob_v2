package com.sza.fastmediasorter.ui.player

import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode

object PlaybackControlPreferences {
    const val PREFS_NAME = "playback_control_dialog"
    const val KEY_LAST_TAB = "last_tab"
    const val KEY_LAST_SECTION = "last_section"
    const val KEY_HUE_DEGREES = "hue_degrees"
    const val KEY_BRIGHTNESS_PERCENT = "brightness_percent"
    const val KEY_LAST_NON_ZERO_VOLUME = "last_non_zero_volume"
    // Speed set via the Control dialog. Stored separately so applyPlayerSettings() can restore it
    // after onPlaybackReady() - which always fires with the default 1.0x from PlayerSettings.
    const val KEY_SPEED = "playback_speed"
    const val KEY_PLAYBACK_ORDER_AUDIO = "playback_order_audio"
    const val KEY_PLAYBACK_ORDER_VIDEO = "playback_order_video"
    const val EXTRA_PLAYBACK_ORDER_OVERRIDE = "extra_playback_order_override"
    // S0241: legacy VR rendering-mode/IPD keys removed alongside the VR runtime.

    fun modeScopeFor(mediaType: MediaType?): String = if (mediaType == MediaType.AUDIO) "audio" else "visual"

    fun globalKeyFor(mediaType: MediaType?): String =
        if (mediaType == MediaType.AUDIO) KEY_PLAYBACK_ORDER_AUDIO else KEY_PLAYBACK_ORDER_VIDEO

    fun resourceKey(resourceId: Long, mediaType: MediaType?): String =
        "playback_order_${modeScopeFor(mediaType)}_$resourceId"

    fun loadMode(
        prefs: SharedPreferences,
        resourceId: Long,
        mediaType: MediaType?
    ): PlaybackOrderMode {
        val resourceValue = prefs.getString(resourceKey(resourceId, mediaType), null)
        if (!resourceValue.isNullOrBlank()) {
            return PlaybackOrderMode.fromPrefsString(resourceValue)
        }
        val globalValue = prefs.getString(globalKeyFor(mediaType), null)
        return PlaybackOrderMode.fromPrefsString(globalValue ?: "")
    }

    fun saveMode(
        prefs: SharedPreferences,
        resourceId: Long,
        mediaType: MediaType?,
        mode: PlaybackOrderMode
    ) {
        val value = mode.toPrefsString()
        prefs.edit()
            .putString(resourceKey(resourceId, mediaType), value)
            .putString(globalKeyFor(mediaType), value)
            .apply()
    }
}
