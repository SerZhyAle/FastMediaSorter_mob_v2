package com.sza.fastmediasorter.ui.player.helpers

/**
 * S1128: pure-Kotlin policy that decides when to force the internet-stream video-quality ceiling down
 * one rendition. Holds no Media3 type, so the decision logic is unit-testable off-device;
 * [StreamPlaybackHelper] is the thin Media3 side that inventories renditions from `Tracks` and applies
 * the returned [Cap] via `DefaultTrackSelector.setParameters(..)`.
 *
 * The trigger is repeated stalls (rebuffer after the first frame), not the bandwidth estimate: Media3's
 * built-in ABR already reacts to bandwidth, so the value here is reacting to a CPU-decode bottleneck the
 * bandwidth-only ABR cannot see. A hysteresis threshold ([STALL_STEP_THRESHOLD]) keeps a single
 * step-induced rebuffer from cascading the quality all the way to the floor.
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
    private var stallsSinceStep = 0

    val renditionCount: Int get() = ladder.size
    val isSingleQuality: Boolean get() = ladder.size <= 1
    val currentCeilingIndex: Int get() = ceilingIndex

    /**
     * Inventory the rendition ladder (ascending by bitrate, then height, then width). Resets the ceiling
     * to the top (unrestricted) and the stall counter, so a mid-session track change re-arms the full
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
        stallsSinceStep = 0
    }

    /**
     * Record one post-first-frame stall. Returns the new ceiling [Cap] when this stall crosses the
     * hysteresis threshold and a lower rung exists; null otherwise (single-quality, already at the floor,
     * or threshold not yet reached). On a step the stall counter resets, so the next step needs another
     * full batch of stalls - a single step-induced rebuffer never cascades.
     */
    fun registerStall(): Cap? {
        // Only a multi-rung ladder accrues stalls; a single-quality / empty ladder never steps.
        if (ladder.size > 1) stallsSinceStep++
        val atThreshold = stallsSinceStep >= STALL_STEP_THRESHOLD
        if (atThreshold) stallsSinceStep = 0
        // Step only when the threshold is reached AND a lower rung exists; at the floor the counter still
        // resets above so it never grows unbounded.
        val shouldStep = atThreshold && ladder.size > 1 && ceilingIndex > 0
        if (!shouldStep) return null
        ceilingIndex--
        val target = ladder[ceilingIndex]
        val bitrateCap = if (target.bitrateBps > 0) target.bitrateBps else Int.MAX_VALUE
        return Cap(target.widthPx, target.heightPx, bitrateCap)
    }

    /** Unknown bitrate sorts to the bottom so it never masks a known-bitrate rung during ordering. */
    private fun sortBitrate(r: Rendition): Int = if (r.bitrateBps > 0) r.bitrateBps else 0

    private companion object {
        const val STALL_STEP_THRESHOLD = 2
    }
}
