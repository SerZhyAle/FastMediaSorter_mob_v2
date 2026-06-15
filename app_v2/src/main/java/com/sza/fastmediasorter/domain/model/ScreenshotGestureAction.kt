package com.sza.fastmediasorter.domain.model

/** Post-capture action assignable to a screenshot edge gesture direction. */
enum class ScreenshotGestureAction {
    SILENT_SCREENSHOT,
    OPEN_IN_PLAYER,
    OPEN_IN_DRAW,
    OCR_TRANSLATE,
    SHARE,
    DO_NOT_USE;

    companion object {
        /** Tolerant DataStore parser: returns the matching constant or [default] for unknown/null values. */
        fun fromName(name: String?, default: ScreenshotGestureAction): ScreenshotGestureAction =
            entries.firstOrNull { it.name == name } ?: default
    }
}
