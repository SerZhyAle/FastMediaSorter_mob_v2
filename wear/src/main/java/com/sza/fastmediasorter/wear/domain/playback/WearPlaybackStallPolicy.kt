package com.sza.fastmediasorter.wear.domain.playback

/**
 * How long a session may want to play without making a sound before it is ended.
 *
 * Comfortably longer than a legitimate buffering pause on watch Wi-Fi, and far shorter than what it
 * takes to empty the battery - the overnight incident of S2848 ran this state for 5 h 15 m.
 *
 * S2849 gave it one home: the background service (S2848) and both player screens answer the same
 * question, and two of them measuring the same silence differently would be a difference nobody
 * could explain from the outside.
 */
const val WEAR_PLAYBACK_STALL_TIMEOUT_MS = 120_000L

/** What a background playback session is actually doing, named so the rule is not asked with two booleans. */
enum class BackgroundPlaybackActivity {
    /** Sound is coming out: the session is doing the one thing it exists for. */
    Playing,

    /**
     * The session still wants to play and produces no sound - buffering, or retrying a stream that
     * stopped answering. Nothing else in the service ends this state, which is why it is named.
     */
    Stalled,

    /** Paused by the owner, ended, or idle: another teardown path already owns this case. */
    Settled
}

/**
 * S2848: tells a stalled background session apart from a playing and from a settled one.
 *
 * The watch drained overnight because `WearPlaybackService` ended itself on exactly three events -
 * `STATE_ENDED`/`STATE_IDLE`, an explicit pause, and the task being removed - and a live audio stream
 * that stops answering hits none of them. It keeps `playWhenReady = true` while producing no sound,
 * so the service stayed foreground for 5 h 15 m and its player refetched the stream the whole time.
 *
 * The rule is a pure function because strategic §7 records that none of the watch players carries a
 * unit test: a decision left inside the service would have no regression net at all.
 *
 * `isEndedOrIdle` is passed as a boolean rather than a Media3 state constant so this stays free of
 * the player library, like every other rule in this package.
 */
object WearPlaybackStallPolicy {

    fun activityOf(
        playWhenReady: Boolean,
        isPlaying: Boolean,
        isEndedOrIdle: Boolean
    ): BackgroundPlaybackActivity = when {
        isEndedOrIdle -> BackgroundPlaybackActivity.Settled
        !playWhenReady -> BackgroundPlaybackActivity.Settled
        isPlaying -> BackgroundPlaybackActivity.Playing
        else -> BackgroundPlaybackActivity.Stalled
    }
}
