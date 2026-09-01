package com.sza.fastmediasorter.domain.model

/**
 * Action assignable to a directional swipe on the launcher All apps panel.
 *
 * S2304: a closed set of its own rather than a filtered view of [LauncherDesktopSwipeAction]. Two of its
 * values act on the panel that is already on screen and mean nothing on the desktop, so the desktop set
 * would have to carry values it is obliged to hide - the same reason screen paging never became a
 * [ScreenshotGestureAction].
 */
sealed interface LauncherAllAppsSwipeAction {

    val persistedName: String

    /** Closes the panel, returning to the desktop underneath it. */
    data object BackToDesktop : LauncherAllAppsSwipeAction {
        override val persistedName: String = "BACK_TO_DESKTOP"
    }

    /**
     * Expands the preview section into the full app list shown instead of the alphabet.
     *
     * S2304: one-way on purpose - the owner named an expand action, not a toggle, so a repeated swipe
     * changes nothing and the section header tap stays the way back to the alphabetical view.
     */
    data object ExpandAllApps : LauncherAllAppsSwipeAction {
        override val persistedName: String = "EXPAND_ALL_APPS"
    }

    /**
     * A shared edge-gesture action. The panel offers only launching a chosen app, locking the screen and
     * the unassigned value, but any stored name resolves so a slot configured by a newer build degrades
     * to that build's action rather than to the default.
     */
    data class EdgeGestureAction(
        val action: ScreenshotGestureAction,
    ) : LauncherAllAppsSwipeAction {
        override val persistedName: String = action.name
    }

    companion object {

        /** The unassigned slot, shared by both horizontal defaults. */
        val Unassigned: LauncherAllAppsSwipeAction =
            EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE)

        fun fromName(
            name: String?,
            default: LauncherAllAppsSwipeAction,
        ): LauncherAllAppsSwipeAction = when (name) {
            BackToDesktop.persistedName -> BackToDesktop
            ExpandAllApps.persistedName -> ExpandAllApps
            null -> default
            else ->
                ScreenshotGestureAction.entries
                    .firstOrNull { it.name == name }
                    ?.let(::EdgeGestureAction)
                    ?: default
        }
    }
}
