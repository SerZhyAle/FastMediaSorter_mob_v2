package com.sza.fastmediasorter.vr.playback

import android.net.Uri

/**
 * Describes the media source for VR playback.
 * Wraps URI + optional metadata needed by the VR engine.
 */
data class VrPlaybackSource(
    val uri: Uri,
    val title: String? = null,
    val mimeType: String? = null
)
