package com.sza.fastmediasorter.wear.domain.playback

/**
 * S2849: whether the screen-off mode may keep the watch display awake.
 *
 * The dark sheet of S1683/S2815 holds the display on so a stream survives the display timeout. The
 * hold had no end condition: a session paused behind the sheet, or one whose stream stopped
 * answering, kept the display lit until the battery ran out - the drain of S2848 with the screen on
 * top of it. What the sheet protects is a session that still wants to play, so that is what the hold
 * is tied to.
 *
 * [isPlaybackRequested] is `playWhenReady`, not `isPlaying`, on purpose: a stream that rebuffers for
 * a few seconds still wants to play, and dropping the hold on every rebuffer would let the watch
 * sleep mid-stream - which pauses it (S0902) and turns a hiccup into an ending. A session that wants
 * to play and never produces sound is ended by [WearPlaybackStallWatchdog] instead, which pauses it
 * and so releases this hold through the same door as a deliberate pause.
 *
 * A pure function because strategic §7 records that none of the watch players carries a unit test.
 */
object WearPlayerDisplayHoldPolicy {

    fun holdsDisplay(isDimmed: Boolean, isPlaybackRequested: Boolean): Boolean =
        isDimmed && isPlaybackRequested
}
