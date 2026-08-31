package com.sza.fastmediasorter.domain.model

/**
 * S2256: the four launcher desktop swipe slots, plus the mapping from a slot to the settings fields that
 * hold its action and its target.
 *
 * The mapping lives here because more than one caller needs it - the row family in launcher settings and
 * the target picker beside it - and four parallel `when` blocks per caller is how a direction ends up
 * reading one field and writing another.
 */
enum class LauncherDesktopSwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    fun actionOf(settings: AppSettings): LauncherDesktopSwipeAction = when (this) {
        UP -> settings.launcherDesktopSwipeUpAction
        DOWN -> settings.launcherDesktopSwipeDownAction
        LEFT -> settings.launcherDesktopSwipeLeftAction
        RIGHT -> settings.launcherDesktopSwipeRightAction
    }

    fun withAction(settings: AppSettings, action: LauncherDesktopSwipeAction): AppSettings = when (this) {
        UP -> settings.copy(launcherDesktopSwipeUpAction = action)
        DOWN -> settings.copy(launcherDesktopSwipeDownAction = action)
        LEFT -> settings.copy(launcherDesktopSwipeLeftAction = action)
        RIGHT -> settings.copy(launcherDesktopSwipeRightAction = action)
    }

    fun payloadOf(settings: AppSettings): String = when (this) {
        UP -> settings.launcherDesktopSwipeUpPayload
        DOWN -> settings.launcherDesktopSwipeDownPayload
        LEFT -> settings.launcherDesktopSwipeLeftPayload
        RIGHT -> settings.launcherDesktopSwipeRightPayload
    }

    fun withPayload(settings: AppSettings, value: String): AppSettings = when (this) {
        UP -> settings.copy(launcherDesktopSwipeUpPayload = value)
        DOWN -> settings.copy(launcherDesktopSwipeDownPayload = value)
        LEFT -> settings.copy(launcherDesktopSwipeLeftPayload = value)
        RIGHT -> settings.copy(launcherDesktopSwipeRightPayload = value)
    }
}
