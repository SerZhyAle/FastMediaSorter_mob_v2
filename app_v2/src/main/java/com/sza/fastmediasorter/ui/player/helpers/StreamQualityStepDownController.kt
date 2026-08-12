package com.sza.fastmediasorter.ui.player.helpers

/**
 * S1128: pure-Kotlin policy that decides when to force the internet-stream video-quality ceiling down
 * one rendition. Holds no Media3 type - and, since S1508, no Android type either - so the decision logic
 * is unit-testable off-device; [StreamPlaybackHelper] is the thin Media3 side that inventories renditions
 * from `Tracks`, supplies the clock, and applies the returned [Cap] via
 * `DefaultTrackSelector.setParameters(..)`.
 *
 * The trigger is repeated stalls (rebuffer after the first frame), not the bandwidth estimate: Media3's
 * built-in ABR already reacts to bandwidth, so the value here is reacting to a CPU-decode bottleneck the
 * bandwidth-only ABR cannot see. A hysteresis threshold ([STALL_STEP_THRESHOLD]) keeps a single
 * step-induced rebuffer from cascading the quality all the way to the floor.
 *
 * S1508: the threshold only means "repeated" because stalls decay out of a [STALL_DECAY_WINDOW_MS]
 * window. Counting them for the whole session made two hiccups an hour apart cost a rung exactly like
 * two consecutive ones, and since there is no step-up path that accidental step lasted until the channel
 * was reopened.
 */
internal class StreamQualityStepDownController {

    /** One selectable video rendition; [bitrateBps] is non-positive when the manifest omits BANDWIDTH. */
    data class Rendition(val widthPx: Int, val heightPx: Int, val bitrateBps: Int)

    /**
     * Ceiling to hand to the track selector: cap both size and bitrate at the stepped-down rendition.
     * [maxBitrateBps] is [Int.MAX_VALUE] when the target rendition has no known bitrate (size cap only).
     */
    data class Cap(val maxWidthPx: Int, val maxHeightPx: Int, val maxBitrateBps: Int)

    private val ladder = mutableListOf<Rendition>()
    private var ceilingIndex = 0

    /** Timestamps of the stalls still inside the decay window, oldest first. */
    private val stallsInWindowMs = ArrayDeque<Long>()

    val renditionCount: Int get() = ladder.size
    val isSingleQuality: Boolean get() = ladder.size <= 1
    val currentCeilingIndex: Int get() = ceilingIndex

    /**
     * Inventory the rendition ladder (ascending by bitrate, then height, then width). Resets the ceiling
     * to the top (unrestricted) and the stall history, so a mid-session track change re-arms the full
     * range. Duplicate renditions are collapsed.
     */
    fun setRenditions(renditions: List<Rendition>) {
        ladder.clear()
        ladder.addAll(
            renditions.distinct().sortedWith(
                compareBy({ sortBitrate(it) }, { it.heightPx }, { it.widthPx }),
            ),
        )
        ceilingIndex = (ladder.size - 1).coerceAtLeast(0)
        stallsInWindowMs.clear()
    }

    /**
     * Record one post-first-frame stall observed at [nowMs] - a monotonic timestamp owned by the caller,
     * because reading the clock here would drag an Android type into a class kept plain-JVM testable.
     * Stalls older than [STALL_DECAY_WINDOW_MS] are dropped first, so only a cluster reaches the
     * threshold. Returns the new ceiling [Cap] when this stall crosses the hysteresis threshold and a
     * lower rung exists; null otherwise (single-quality, already at the floor, or threshold not yet
     * reached). On a step the window is cleared, so the next step needs another full batch of stalls -
     * a single step-induced rebuffer never cascades.
     */
    fun registerStall(nowMs: Long, playing: Rendition? = null): Cap? {
        // Only a multi-rung ladder accrues stalls; a single-quality / empty ladder never steps.
        if (ladder.size > 1) {
            val cutoffMs = nowMs - STALL_DECAY_WINDOW_MS
            // A stall exactly on the window edge still counts; anything strictly older has decayed.
            while (stallsInWindowMs.isNotEmpty() && stallsInWindowMs.first() < cutoffMs) {
                stallsInWindowMs.removeFirst()
            }
            stallsInWindowMs.addLast(nowMs)
        }
        val atThreshold = stallsInWindowMs.size >= STALL_STEP_THRESHOLD
        if (atThreshold) stallsInWindowMs.clear()
        // S1514: step down from whatever is ACTUALLY playing, not from the ceiling. The two are different
        // numbers: the ceiling is only an upper bound and Media3's ABR moves freely below it, so on a
        // channel where ABR already sits near the floor, decrementing the ceiling can land above the
        // picture and change nothing at all. A five-rung ladder could then burn up to eight stalls before
        // the ceiling finally bit - while the log reported a step down each time.
        val anchor = playing?.let { indexOfPlaying(it) }?.coerceAtMost(ceilingIndex) ?: ceilingIndex
        // Step only when the threshold is reached AND a lower rung exists below the anchor; at the floor
        // the window still clears above so it never grows unbounded.
        val shouldStep = atThreshold && ladder.size > 1 && anchor > 0
        if (!shouldStep) return null
        ceilingIndex = anchor - 1
        val target = ladder[ceilingIndex]
        val bitrateCap = if (target.bitrateBps > 0) target.bitrateBps else Int.MAX_VALUE
        return Cap(target.widthPx, target.heightPx, bitrateCap)
    }

    /**
     * S1514: where [playing] sits on the ladder, or null when it cannot be placed at all.
     *
     * An exact size match is the normal case. The fallback exists because the format the renderer reports
     * need not be verbatim one of the inventoried renditions - a manifest can describe a rung the decoder
     * then reports slightly differently - and the height alone still says how tall the picture is, which
     * is all the anchor needs.
     */
    fun indexOfPlaying(playing: Rendition): Int? {
        val exact = ladder.indexOfFirst { it.widthPx == playing.widthPx && it.heightPx == playing.heightPx }
        val resolved = if (exact >= 0) exact else ladder.indexOfLast { it.heightPx <= playing.heightPx }
        return resolved.takeIf { it >= 0 }
    }

    /** Unknown bitrate sorts to the bottom so it never masks a known-bitrate rung during ordering. */
    private fun sortBitrate(r: Rendition): Int = if (r.bitrateBps > 0) r.bitrateBps else 0

    private companion object {
        const val STALL_STEP_THRESHOLD = 2

        // Matched to the stall detector's own scale (~9 s to declare a position freeze, 15 s buffering
        // timeout - StreamStallWatchdog) and to the StreamsPlayer governor's 120 s starvation window.
        const val STALL_DECAY_WINDOW_MS = 120_000L
    }
}
