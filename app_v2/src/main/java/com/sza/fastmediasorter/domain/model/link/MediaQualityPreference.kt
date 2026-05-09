package com.sza.fastmediasorter.domain.model.link

/**
 * S0116 §5.1 pillar J: format/quality preference applied to streaming variant
 * selection and direct-file candidates with declared resolution.
 *
 * `maxResolutionPx` — vertical resolution ceiling in pixels; `Int.MAX_VALUE`
 * encodes "best available". `audioOnly` — request an audio-only rendition when
 * the manifest exposes one (no-op otherwise).
 */
data class MediaQualityPreference(
    val maxResolutionPx: Int,
    val audioOnly: Boolean,
) {
    companion object {
        fun fromSettings(maxResolution: String, audioOnly: Boolean): MediaQualityPreference {
            val px = when (maxResolution) {
                "480p" -> 480
                "720p" -> 720
                "1080p" -> 1080
                "best" -> Int.MAX_VALUE
                else -> 1080 // safe default; settings whitelist already guarantees one of the four above
            }
            return MediaQualityPreference(maxResolutionPx = px, audioOnly = audioOnly)
        }
    }
}
