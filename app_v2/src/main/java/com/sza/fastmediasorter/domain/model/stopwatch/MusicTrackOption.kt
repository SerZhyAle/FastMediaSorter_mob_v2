package com.sza.fastmediasorter.domain.model.stopwatch

import android.net.Uri

/**
 * One audio track of the device media store, as a music-picker option (S2792).
 *
 * Deliberately a picker-shaped projection - title, artist, duration, uri - and not a full audio
 * metadata record: the picker paints one row per track and returns the chosen uri to the caller's
 * own persistence, so carrying anything heavier would tax every row for a value nobody reads.
 */
data class MusicTrackOption(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val durationMillis: Long,
)
