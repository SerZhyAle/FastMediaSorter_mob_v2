package com.sza.fastmediasorter.wear.domain.model

/**
 * How the watch video player fits a frame to the display.
 *
 * S1948: a domain type rather than a screen one, because the watch settings store persists it and a
 * domain repository must not import from `ui/player/video`.
 */
enum class VideoScaleMode {
    FIT,
    CROP_PAN;

    companion object {
        /** Stored preferences predate this value, so an unknown or absent name keeps today's fit behaviour. */
        fun fromNameOrDefault(name: String?): VideoScaleMode =
            entries.firstOrNull { it.name == name } ?: FIT
    }
}
