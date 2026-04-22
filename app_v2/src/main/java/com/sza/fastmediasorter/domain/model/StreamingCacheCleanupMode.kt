package com.sza.fastmediasorter.domain.model

/**
 * User preference controlling what happens to a streaming-offload local copy
 * when the player exits or the user navigates to a different file.
 *
 * - [ASK]: show `CleanupPromptDialog` (default)
 * - [AUTO_DELETE]: silently delete the local copy on player exit
 * - [AUTO_KEEP]: retain the local copy; TTL-based eviction still applies
 */
enum class StreamingCacheCleanupMode {
    ASK,
    AUTO_DELETE,
    AUTO_KEEP;

    companion object {
        val DEFAULT: StreamingCacheCleanupMode = ASK

        fun fromName(name: String?): StreamingCacheCleanupMode =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
