package com.sza.fastmediasorter.domain.model

/**
 * Action assignable to a directional swipe on the launcher desktop.
 *
 * The launcher-only All apps route stays local so it can reuse the already open desktop task. Every
 * other value wraps the shared edge-gesture action instead of maintaining a second action list.
 */
sealed interface LauncherDesktopSwipeAction {

    val persistedName: String

    data object OpenAllApps : LauncherDesktopSwipeAction {
        override val persistedName: String = "OPEN_ALL_APPS"
    }

    data class EdgeGestureAction(
        val action: ScreenshotGestureAction,
    ) : LauncherDesktopSwipeAction {
        override val persistedName: String = action.name
    }

    companion object {
        fun fromName(
            name: String?,
            default: LauncherDesktopSwipeAction,
        ): LauncherDesktopSwipeAction = when (name) {
            OpenAllApps.persistedName -> OpenAllApps
            null -> default
            else -> ScreenshotGestureAction.entries
                .firstOrNull { it.name == name }
                ?.let(::EdgeGestureAction)
                ?: default
        }
    }
}
