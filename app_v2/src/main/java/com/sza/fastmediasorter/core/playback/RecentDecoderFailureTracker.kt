package com.sza.fastmediasorter.core.playback

/**
 * Cooldown tracker for video sources that recently failed with a decoder error.
 *
 * Used by S0213 §5.1 Pillar A - keeps a short in-memory window after a MediaCodec / FFmpeg
 * decoder failure so that the player does not immediately re-create heavy native graphs for the
 * same source. Cooldown lifetime is bounded by [DECODER_COOLDOWN_MS]; entries are also cleared
 * on the next successful playback of any source via [clearAll].
 *
 * Lookup is O(1) and the implementation must be safe to call from any thread.
 */
interface RecentDecoderFailureTracker {

    /** Record [path] as failed at the current monotonic clock time. */
    fun markFailed(path: String)

    /** Return true if [path] is still within the cooldown window. */
    fun isInCooldown(path: String): Boolean

    /**
     * Return how many milliseconds remain until [path] leaves the cooldown window.
     * Zero if [path] is not currently marked.
     */
    fun cooldownRemainingMs(path: String): Long

    /** Drop every tracked entry. Called on a successful frame render from any source. */
    fun clearAll()

    companion object {

        /** Cooldown duration after a decoder failure (S0213 §6 Q1 resolved 2026-05-15). */
        const val DECODER_COOLDOWN_MS: Long = 45_000L
    }
}
