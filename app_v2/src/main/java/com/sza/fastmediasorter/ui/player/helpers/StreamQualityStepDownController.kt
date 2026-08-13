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

    /**
     * S1511: everything a session needs to hand to the store and get back next time - the rung the ceiling
     * settled on and how many probes above it have failed. A value type rather than a repository call is
     * what lets the policy stay free of any storage type at all.
     */
    data class Memory(val rung: Rendition, val probeFailures: Int)

    private val ladder = mutableListOf<Rendition>()
    private var ceilingIndex = 0

    /** Timestamps of the stalls still inside the decay window, oldest first. */
    private val stallsInWindowMs = ArrayDeque<Long>()

    /**
     * S1511: how many probes up to each rung have already failed. Per rung rather than per channel, because
     * a line that cannot carry 1080p may carry 720p perfectly well, and one shared counter would punish the
     * rung that never failed.
     */
    private val probeFailuresByRung = mutableMapOf<Rendition, Int>()

    /**
     * S1511: when the ceiling last moved, which is what the probe wait is measured from. Null until the
     * first timed call, because the ladder arrives before any clock is offered.
     */
    private var ceilingSinceMs: Long? = null

    /** S1511: whether a probe upward is currently open, and what it reached for. */
    sealed interface ProbeState {
        data object Idle : ProbeState

        /** [fromIndex] is the ceiling to restore on failure; [probedRung] is what the failure is charged to. */
        data class Open(val fromIndex: Int, val probedRung: Rendition, val startedMs: Long) : ProbeState
    }

    private var probeState: ProbeState = ProbeState.Idle

    val isProbeOpen: Boolean get() = probeState is ProbeState.Open

    val renditionCount: Int get() = ladder.size
    val isSingleQuality: Boolean get() = ladder.size <= 1
    val currentCeilingIndex: Int get() = ceilingIndex

    /**
     * Inventory the rendition ladder (ascending by bitrate, then height, then width). Resets the ceiling
     * to the top (unrestricted) and the stall history, so a mid-session track change re-arms the full
     * range. Duplicate renditions are collapsed.
     *
     * S1511: [remembered] is what a previous session learned about this channel. The ceiling is placed on
     * the rung with that bitrate, or on the nearest rung below it when the source has re-encoded and the
     * exact rung is gone; a remembered rung above everything on this ladder leaves the ceiling at the top,
     * because a memory must never restrict a channel to a rung it no longer offers. Returns the rung the
     * ceiling actually landed on so the caller can log which of those three happened.
     */
    fun setRenditions(renditions: List<Rendition>, remembered: Memory? = null): Rendition? {
        ladder.clear()
        ladder.addAll(
            renditions.distinct().sortedWith(
                compareBy({ sortBitrate(it) }, { it.heightPx }, { it.widthPx }),
            ),
        )
        ceilingIndex = (ladder.size - 1).coerceAtLeast(0)
        stallsInWindowMs.clear()
        probeFailuresByRung.clear()
        ceilingSinceMs = null
        probeState = ProbeState.Idle
        remembered?.let { restoreFrom(it) }
        return ladder.getOrNull(ceilingIndex)
    }

    /**
     * S1511: what this session has learned so far, for the caller to persist. Null on an empty ladder,
     * where there is no rung to name. The failure count belongs to the rung above the ceiling - the one a
     * probe would reach for - because that is the number the next session's wait is computed from.
     */
    val memory: Memory?
        get() {
            val ceiling = ladder.getOrNull(ceilingIndex) ?: return null
            val above = ladder.getOrNull(ceilingIndex + 1)
            return Memory(ceiling, above?.let { probeFailuresByRung[it] } ?: 0)
        }

    private fun restoreFrom(remembered: Memory) {
        ceilingIndex = indexOfRemembered(remembered.rung) ?: ceilingIndex
        ladder.getOrNull(ceilingIndex + 1)?.let { probeFailuresByRung[it] = remembered.probeFailures }
    }

    /**
     * S1511: where a remembered rung sits on the current ladder. An exact bitrate match is the normal case;
     * the fallback to the highest rung at or below it covers a source that changed encoding, where the
     * remembered number names no rung but still says how much this line was known to carry. Null when every
     * rung is above the remembered one, or when neither side has a usable bitrate.
     */
    private fun indexOfRemembered(remembered: Rendition): Int? {
        val exact = ladder.indexOfFirst { it.bitrateBps == remembered.bitrateBps && it.bitrateBps > 0 }
        val resolved = if (exact >= 0) {
            exact
        } else {
            ladder.indexOfLast { it.bitrateBps in 1..remembered.bitrateBps }
        }
        return resolved.takeIf { it >= 0 }
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
        // S1511: a stall arriving while a probe is open is that probe's verdict, not evidence about the
        // rung below it - the ceiling goes back where it came from and the failure is charged to the rung
        // that was reached for, so the step-down ladder is left exactly as the probe found it.
        val openProbe = probeState as? ProbeState.Open
        return if (openProbe != null) failProbe(openProbe, nowMs) else stepDownOnStall(nowMs, playing)
    }

    private fun stepDownOnStall(nowMs: Long, playing: Rendition?): Cap? {
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
        // S1511: the probe wait runs from the last ceiling move, so a step down restarts it.
        ceilingSinceMs = nowMs
        return capOf(ladder[ceilingIndex])
    }

    /**
     * S1511: raise the ceiling one rung and open a probe, returning the [Cap] to apply. Null when a probe
     * is already open or the ceiling is at the top. The probe is explicit state rather than something read
     * back off the ceiling index, because a step down and a probe leave the index looking identical while
     * meaning opposite things.
     */
    fun startProbe(nowMs: Long): Cap? {
        val target = ladder.getOrNull(ceilingIndex + 1)
        if (target == null || probeState is ProbeState.Open) return null
        probeState = ProbeState.Open(ceilingIndex, target, nowMs)
        ceilingIndex += 1
        return capOf(target)
    }

    /**
     * S1511: close an open probe as successful when it has survived the full [PROBE_OBSERVATION_WINDOW_MS]
     * and [playing] shows the picture actually reached the probed rung. Success forgives only that rung's
     * failure count; every other rung keeps whatever it earned. Returns false while the window is still
     * running, so the caller may ask on every tick.
     *
     * The [playing] check is the guard strategic section 7 names: a probe can outlive its window while the
     * engine never climbed, and recording that as success would remember a rung which rendered no frame.
     */
    fun closeProbeIfSurvived(nowMs: Long, playing: Rendition? = null): Boolean {
        val open = probeState as? ProbeState.Open ?: return false
        val reachedProbedRung = playing == null || (indexOfPlaying(playing) ?: -1) >= ceilingIndex
        val survived = nowMs - open.startedMs >= PROBE_OBSERVATION_WINDOW_MS && reachedProbedRung
        if (survived) {
            probeFailuresByRung.remove(open.probedRung)
            probeState = ProbeState.Idle
            ceilingSinceMs = nowMs
        }
        return survived
    }

    /** Put the ceiling back where the probe found it and charge the failure to the rung it reached for. */
    private fun failProbe(open: ProbeState.Open, nowMs: Long): Cap {
        probeFailuresByRung[open.probedRung] = (probeFailuresByRung[open.probedRung] ?: 0) + 1
        probeState = ProbeState.Idle
        ceilingIndex = open.fromIndex
        ceilingSinceMs = nowMs
        stallsInWindowMs.clear()
        return capOf(ladder[open.fromIndex])
    }

    /** [Cap.maxBitrateBps] is [Int.MAX_VALUE] when the rung has no known bitrate, leaving a size-only cap. */
    private fun capOf(rung: Rendition): Cap =
        Cap(rung.widthPx, rung.heightPx, if (rung.bitrateBps > 0) rung.bitrateBps else Int.MAX_VALUE)

    /**
     * S1511: is it time to try the rung above the current ceiling, at the caller-supplied monotonic
     * [nowMs]? Never on a single-rung ladder and never at the top - there is nothing above to reach for.
     *
     * The first call only anchors the wait and answers false: the ladder becomes known in
     * [setRenditions], which is offered no clock, so the session has no measurable age until a tick
     * arrives. A probe is a real switch that costs the viewer something, so asking too early is the
     * expensive mistake, not asking too late.
     */
    fun isProbeDue(nowMs: Long): Boolean {
        val anchor = ceilingSinceMs
        if (anchor == null) {
            ceilingSinceMs = nowMs
            return false
        }
        return probeState is ProbeState.Idle && ladder.size > 1 && ceilingIndex < ladder.size - 1 &&
            nowMs - anchor >= nextProbeWaitMs()
    }

    /**
     * S1511: the wait owed before the rung above the ceiling may be tried again. It doubles once per
     * recorded failure of *that* rung and stops at [PROBE_MAX_WAIT_MS], so a rung this line genuinely
     * cannot carry backs off towards hourly instead of costing a stall every few minutes.
     */
    private fun nextProbeWaitMs(): Long {
        val target = ladder.getOrNull(ceilingIndex + 1) ?: return PROBE_BASE_WAIT_MS
        val doublings = (probeFailuresByRung[target] ?: 0).coerceAtMost(PROBE_WAIT_DOUBLINGS_MAX)
        return (PROBE_BASE_WAIT_MS shl doublings).coerceAtMost(PROBE_MAX_WAIT_MS)
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

        // Measured 2026-08-13 on a Galaxy S21+ over Wi-Fi, on a multi-variant HLS: raising the ceiling
        // leaves 3.0-3.5 s with no rendered frame and takes 4.0 s to reach steady frame rate, and the
        // renderer's frame counter resets across the switch - so it re-initialises even though no
        // `prepare` is re-issued. That is the bottom of the 3-18 s the StreamsPlayer source measured for
        // its own re-open, not the near-free switch this ticket assumed, so its arithmetic is adopted
        // rather than shortened: their 60 s base cost 108.9 s of black screen per session against 36.0 s
        // at a 5 min base, and our probe is not cheap enough to buy a faster cadence.
        const val PROBE_BASE_WAIT_MS = 300_000L
        const val PROBE_MAX_WAIT_MS = 3_600_000L
        const val PROBE_WAIT_DOUBLINGS_MAX = 4

        // A probe must outlive the window a stall cluster needs to form, or "it survived" would only mean
        // "nothing had time to go wrong yet" - hence the same span the step-down rule already decays over.
        const val PROBE_OBSERVATION_WINDOW_MS = STALL_DECAY_WINDOW_MS
    }
}
