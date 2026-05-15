package com.sza.fastmediasorter.core.memory

/**
 * Lifecycle anchors at which a memory measurement is taken.
 *
 * Each value names a fixed point in the user-visible flow where heap and native
 * memory pressure should be observable to a developer reading a single session
 * log. The order of declaration matches the canonical scenario
 * `cold start -> SFTP browse -> tap MP3` documented in S0207.
 *
 * S0207 — Phase 01 (memory-instrumentation).
 */
enum class MemoryCheckpoint {
    /** End of `Application.onCreate()` — after Timber init and startup banner. */
    APP_STARTED,

    /** First main screen rendered — posted after `MainActivity.setContentView`. */
    MAIN_DRAWN,

    /** Browse screen opened — at the end of `BrowseActivity.onCreate`. */
    BROWSE_OPENED,

    /** All visible thumbnails for the current scroll window finished loading. */
    THUMBNAILS_LOADED,

    /** Just before the player engages a media source for the chosen item. */
    PRE_PLAY,

    /** Player reached `STATE_READY` — first decoded frame / audio buffer is queued. */
    AFTER_STATE_READY,
}
