package com.sza.fastmediasorter.domain.model

/** Action assignable to a directional swipe on the launcher desktop. */
enum class LauncherDesktopSwipeAction {
    OPEN_ALL_APPS,
    OPEN_NOTIFICATION_SHADE,
    DO_NOT_USE;

    companion object {
        /** Returns [default] when a persisted token is absent or unknown. */
        fun fromName(name: String?, default: LauncherDesktopSwipeAction): LauncherDesktopSwipeAction =
            entries.firstOrNull { it.name == name } ?: default
    }
}
