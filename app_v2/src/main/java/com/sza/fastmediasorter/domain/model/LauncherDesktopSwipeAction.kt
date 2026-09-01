package com.sza.fastmediasorter.domain.model

/**
 * Action assignable to a directional swipe on the launcher desktop.
 *
 * The launcher-only routes stay local so they can act on the already open desktop task: All apps
 * reuses it, and the two paging routes change which screen it draws. Every other value wraps the
 * shared edge-gesture action instead of maintaining a second action list.
 *
 * S2301: paging is deliberately NOT a [ScreenshotGestureAction]. An edge gesture fires with no
 * launcher on screen, where "next screen" names nothing that exists, so the shared list would carry an
 * action that can never run - the same reason All apps is local.
 */
sealed interface LauncherDesktopSwipeAction {

    val persistedName: String

    /**
     * S2256: spells the same token as [ScreenshotGestureAction.OPEN_ALL_APPS] on purpose - [fromName]
     * checks this branch first, so a desktop swipe reuses the already-open home task while the edge
     * slot with the same token goes through the launcher seam.
     */
    data object OpenAllApps : LauncherDesktopSwipeAction {
        override val persistedName: String = "OPEN_ALL_APPS"
    }

    /** S2301: draws the next launcher screen, stopping at the last one. */
    data object NextScreen : LauncherDesktopSwipeAction {
        override val persistedName: String = "NEXT_SCREEN"
    }

    /** S2301: draws the previous launcher screen, stopping at the first one. */
    data object PreviousScreen : LauncherDesktopSwipeAction {
        override val persistedName: String = "PREVIOUS_SCREEN"
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
            NextScreen.persistedName -> NextScreen
            PreviousScreen.persistedName -> PreviousScreen
            null -> default
            else ->
                ScreenshotGestureAction.entries
                    .firstOrNull { it.name == name }
                    ?.let(::EdgeGestureAction)
                    ?: default
        }
    }
}
