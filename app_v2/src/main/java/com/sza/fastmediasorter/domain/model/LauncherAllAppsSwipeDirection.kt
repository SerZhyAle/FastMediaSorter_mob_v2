package com.sza.fastmediasorter.domain.model

/**
 * S2304: the four All apps panel swipe slots, plus the mapping from a slot to the settings fields that
 * hold its action and its target.
 *
 * The mapping lives here for the reason [LauncherDesktopSwipeDirection] gives for its own: more than one
 * caller needs it, and four parallel `when` blocks per caller is how a direction ends up reading one
 * field and writing another.
 */
enum class LauncherAllAppsSwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    fun actionOf(settings: AppSettings): LauncherAllAppsSwipeAction = when (this) {
        UP -> settings.launcherAllAppsSwipeUpAction
        DOWN -> settings.launcherAllAppsSwipeDownAction
        LEFT -> settings.launcherAllAppsSwipeLeftAction
        RIGHT -> settings.launcherAllAppsSwipeRightAction
    }

    fun withAction(settings: AppSettings, action: LauncherAllAppsSwipeAction): AppSettings = when (this) {
        UP -> settings.withLauncher { copy(allAppsSwipeUpAction = action) }
        DOWN -> settings.withLauncher { copy(allAppsSwipeDownAction = action) }
        LEFT -> settings.withLauncher { copy(allAppsSwipeLeftAction = action) }
        RIGHT -> settings.withLauncher { copy(allAppsSwipeRightAction = action) }
    }

    fun payloadOf(settings: AppSettings): String = when (this) {
        UP -> settings.launcherAllAppsSwipeUpPayload
        DOWN -> settings.launcherAllAppsSwipeDownPayload
        LEFT -> settings.launcherAllAppsSwipeLeftPayload
        RIGHT -> settings.launcherAllAppsSwipeRightPayload
    }

    fun withPayload(settings: AppSettings, value: String): AppSettings = when (this) {
        UP -> settings.withLauncher { copy(allAppsSwipeUpPayload = value) }
        DOWN -> settings.withLauncher { copy(allAppsSwipeDownPayload = value) }
        LEFT -> settings.withLauncher { copy(allAppsSwipeLeftPayload = value) }
        RIGHT -> settings.withLauncher { copy(allAppsSwipeRightPayload = value) }
    }
}
