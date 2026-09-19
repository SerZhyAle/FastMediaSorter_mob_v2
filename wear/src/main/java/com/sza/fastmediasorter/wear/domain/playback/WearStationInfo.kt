package com.sza.fastmediasorter.wear.domain.playback

/**
 * S3099: what a live stream says about itself.
 *
 * A broadcast has no duration, so the player row that shows one for a file has nothing true to put
 * there. These fields replace it: the first three come from the ICY headers the station sends with
 * the stream, the codec from the audio format ExoPlayer ends up decoding.
 */
data class WearStationInfo(
    val name: String? = null,
    val genre: String? = null,
    val bitrateKbps: Int? = null,
    val codec: String? = null,
    val resolution: String? = null
) {

    val isEmpty: Boolean
        get() = name.isNullOrBlank() && genre.isNullOrBlank() && bitrateKbps == null &&
            codec.isNullOrBlank() && resolution.isNullOrBlank()

    /**
     * The non-empty fields in display order.
     *
     * The bitrate's unit lives in a string resource, so the caller renders that one part and this
     * class stays free of Android types.
     */
    fun textParts(bitrateLabel: (Int) -> String): List<String> = listOfNotNull(
        name?.takeIf { it.isNotBlank() },
        genre?.takeIf { it.isNotBlank() },
        resolution?.takeIf { it.isNotBlank() },
        codec?.takeIf { it.isNotBlank() },
        bitrateKbps?.takeIf { it > 0 }?.let(bitrateLabel)
    )

    companion object {

        private val CODEC_LABELS = mapOf(
            // Audio codecs
            "audio/mpeg" to "MP3",
            "audio/mpeg-l1" to "MP3",
            "audio/mpeg-l2" to "MP3",
            "audio/aac" to "AAC",
            "audio/mp4a-latm" to "AAC",
            "audio/opus" to "Opus",
            "audio/ogg" to "Vorbis",
            "audio/vorbis" to "Vorbis",
            "audio/flac" to "FLAC",
            // Video codecs
            "video/avc" to "H.264",
            "video/mp4v-es" to "MPEG-4",
            "video/hevc" to "H.265",
            "video/x-vnd.on2.vp8" to "VP8",
            "video/x-vnd.on2.vp9" to "VP9",
            "video/av01" to "AV1",
            "video/3gpp" to "H.263"
        )

        /** A codec name a listener recognises, or null rather than a raw MIME type on the glass. */
        fun codecLabel(mimeType: String?): String? =
            mimeType?.trim()?.lowercase()?.let(CODEC_LABELS::get)
    }
}
