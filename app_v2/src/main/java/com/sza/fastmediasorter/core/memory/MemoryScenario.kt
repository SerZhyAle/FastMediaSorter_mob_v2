package com.sza.fastmediasorter.core.memory

/** Process is not inside a memory-sensitive UI or playback scenario. */
enum class MemoryScenario {
    /** No active browse/player workload should keep image-heavy memory warm. */
    IDLE,

    /** File list / browser UI is visible and may request lightweight thumbnails. */
    BROWSE_LIST,

    /** Gallery-style browsing is visible and may need a richer thumbnail profile. */
    BROWSE_GALLERY,

    /** Audio playback is active; image memory should stay on the cheapest default path. */
    AUDIO_PLAYBACK,

    /** Video playback is active and should preserve the normal visual path. */
    VIDEO_PLAYBACK,
}