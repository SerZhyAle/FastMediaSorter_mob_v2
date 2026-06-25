package com.sza.fastmediasorter.domain.model

/**
 * Action assignable to an edge gesture direction. Most actions capture the screen first and then run
 * a post-capture route; [OPEN_APP], [OPEN_PANEL] and [DO_NOT_USE] are pre-capture actions that skip
 * capture entirely.
 */
enum class ScreenshotGestureAction {
    SILENT_SCREENSHOT,
    OPEN_IN_PLAYER,
    OPEN_IN_DRAW,
    OCR_TRANSLATE,
    SEND_TO_RECIPIENTS,
    SHARE,
    CROP_AND_SHARE,
    OPEN_APP,
    OPEN_PANEL,
    DO_NOT_USE;

    companion object {
        /** Tolerant DataStore parser: returns the matching constant or [default] for unknown/null values. */
        fun fromName(name: String?, default: ScreenshotGestureAction): ScreenshotGestureAction =
            entries.firstOrNull { it.name == name } ?: default
    }
}
