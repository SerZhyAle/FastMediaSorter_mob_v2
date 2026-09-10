package com.sza.fastmediasorter.service

/**
 * S2881: the listening session's view of playback, narrow enough that the session owner needs no
 * Android types at all.
 *
 * The port exists because [WatchListenSessionManager] moved out of a ViewModel: a class in `service`
 * reaching into `ui/player/helpers` would invert the layer order, and the session's state machine -
 * request ids, the answer timeout, the record flag - is the part worth testing on the JVM, which a
 * direct `AudioServiceController` dependency would make impossible.
 */
interface WatchListenPlayback {

    /**
     * Announces that a session is about to be requested, before the command leaves for the watch.
     *
     * Separate from [start] because the watch's address can come back fast enough that the audio
     * service is created - and its load control built - before a flag written on the answer would
     * have reached disk.
     */
    fun prepareSession()

    /**
     * Plays the watch's stream at [url].
     *
     * @param onPlaying invoked once the player has accepted the source.
     * @param onDropped invoked when a playing session fails mid-stream.
     */
    fun start(url: String, onPlaying: () -> Unit, onDropped: () -> Unit)

    /** Stops and releases. Safe to call when nothing is playing, and safe to call twice. */
    fun stop()
}
