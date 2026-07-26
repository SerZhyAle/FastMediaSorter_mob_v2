package com.sza.fastmediasorter.core.playback

/**
 * S1142: best-effort split of an ICY `StreamTitle` into artist and title. ICY delivers a single
 * free-form string (usually `Artist - Title`, but a station may send only a title or ad/service text),
 * so the split is heuristic: [artist] is null whenever no ` - ` separator is present. Pure JVM (no
 * Android deps) so it unit-tests without Robolectric. Shared by the background service (separate
 * Artist/Title notification fields) and the inline/grid formatter (single display line).
 */
data class NowPlayingMetadata(val artist: String?, val title: String) {

    /** Single-line rendering: `Artist - Title` when an artist is known, otherwise the bare title. */
    fun trackLine(): String = if (artist.isNullOrBlank()) title else "$artist - $title"

    companion object {
        private const val SEPARATOR = " - "

        /**
         * Parses a raw ICY [streamTitle]. Returns null for a blank string; splits on the FIRST
         * [SEPARATOR] only (a title may itself contain ` - `); falls back to artist=null / whole
         * string as title when there is no separator or either side trims to empty.
         */
        fun parse(streamTitle: String): NowPlayingMetadata? {
            val trimmed = streamTitle.trim()
            if (trimmed.isEmpty()) return null
            val index = trimmed.indexOf(SEPARATOR)
            if (index <= 0) return NowPlayingMetadata(artist = null, title = trimmed)
            val artist = trimmed.substring(0, index).trim()
            val title = trimmed.substring(index + SEPARATOR.length).trim()
            return if (artist.isEmpty() || title.isEmpty()) {
                NowPlayingMetadata(artist = null, title = trimmed)
            } else {
                NowPlayingMetadata(artist = artist, title = title)
            }
        }
    }
}
