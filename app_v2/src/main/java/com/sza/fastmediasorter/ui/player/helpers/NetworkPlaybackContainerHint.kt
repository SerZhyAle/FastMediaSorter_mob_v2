package com.sza.fastmediasorter.ui.player.helpers

enum class NetworkPlaybackContainerHint {
    M2TS_TS_CANDIDATE,
    DVD_PS_VOB,
    OTHER;

    companion object {
        fun fromPath(path: String): NetworkPlaybackContainerHint {
            val lower = path.lowercase()
            return when {
                lower.endsWith(".m2ts") || lower.endsWith(".m2t") -> M2TS_TS_CANDIDATE
                lower.endsWith(".vob") -> DVD_PS_VOB
                else -> OTHER
            }
        }
    }
}
