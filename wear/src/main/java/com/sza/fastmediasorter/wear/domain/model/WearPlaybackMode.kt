package com.sza.fastmediasorter.wear.domain.model

/**
 * Traversal and order modes for media playback on Wear OS players (image, video, audio).
 */
enum class WearPlaybackMode {
    /** Linear sequence traversal through files in original order. */
    SEQUENTIAL,

    /** Random / shuffled order traversal through files. */
    SHUFFLE,

    /** Loop / repeat traversal mode. */
    LOOP;

    /** Cycle to the next playback mode in order: SEQUENTIAL -> SHUFFLE -> LOOP -> SEQUENTIAL. */
    fun next(): WearPlaybackMode = when (this) {
        SEQUENTIAL -> SHUFFLE
        SHUFFLE -> LOOP
        LOOP -> SEQUENTIAL
    }
}
