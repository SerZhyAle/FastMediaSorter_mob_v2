package com.sza.fastmediasorter.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * S3557: the launcher clock dial's display choices, observable so a change reaches the watch at once.
 *
 * Flavors without a launcher bind an implementation that never emits: they have no dial to mirror.
 */
interface ClockDialStyleSource {

    /** Emits the current style on collect and again after every change. */
    fun observe(): Flow<ClockDialStyle>
}

data class ClockDialStyle(
    val secondsVisible: Boolean,
    // ARGB; null means the dial follows the theme colour.
    val dialColor: Int?,
    val typefaceName: String,
)
