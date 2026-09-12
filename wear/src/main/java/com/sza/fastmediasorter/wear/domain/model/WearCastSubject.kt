package com.sza.fastmediasorter.wear.domain.model

/**
 * S2531: what a watch screen is showing, in the terms the cast request is built from.
 *
 * Watch-only - it never crosses the bridge, so it is not mirrored in the phone module. Its job is to
 * make the one content kind the phone cannot reach a case of its own rather than a missing field.
 */
sealed interface WearCastSubject {

    /**
     * A catalogue or pinned stream: the URL is public, so any device with a network can play it.
     *
     * Carries the address rather than the catalogue row, because the three player screens reach a
     * stream by three different routes and only two of them ever hold a [WearStreamChannel].
     */
    data class Stream(
        val url: String,
        val displayName: String,
        val mediaType: WearCastMediaType
    ) : WearCastSubject

    /**
     * A file inside a network source this watch received from the phone during a sync.
     *
     * [relativePath] is the path within the share, exactly as this watch browsed it; the phone rebuilds
     * its own address from the pair rather than being sent one (strategic 6.2).
     */
    data class NetworkFile(
        val source: NetworkSource,
        val relativePath: String,
        val displayName: String,
        val mediaType: WearCastMediaType
    ) : WearCastSubject

    /**
     * Anything held only in this watch's own storage, including a file the phone sent earlier.
     *
     * Its identifier is a row in this watch's media library and means nothing anywhere else, so the
     * refusal belongs here where the origin is known - sending it would come back as a fault from the
     * phone for content that was never addressable (strategic 2 Non-goals).
     */
    data class WatchLocalFile(val displayName: String) : WearCastSubject
}

/** S2531: what came of one attempt to cast from this watch. */
sealed interface WearCastAttempt {

    /** The phone was asked and answered. */
    data class Answered(val outcome: WearCastOutcome) : WearCastAttempt

    /** Nothing was sent: this content has no address the phone could resolve. */
    data object NotCastable : WearCastAttempt
}
