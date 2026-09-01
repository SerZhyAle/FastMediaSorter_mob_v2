package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * S2265: which kind of target a gesture action stores in its per-slot payload, and so which chooser its
 * target row opens and which wording that row carries. `null` means the action takes no target at all,
 * which is what hides the row.
 *
 * One home for the mapping: the edge slots and the launcher desktop swipes both bind
 * [ScreenshotGestureAction] to a payload, and a second copy of this `when` would let the two surfaces
 * disagree about whether an action is configurable - which is exactly the gap S2265 was filed for.
 */
enum class GestureTargetKind {
    APP,
    URL,
    ;

    companion object {
        fun of(action: ScreenshotGestureAction): GestureTargetKind? = when (action) {
            ScreenshotGestureAction.OPEN_APP -> APP
            ScreenshotGestureAction.OPEN_URL -> URL
            else -> null
        }
    }
}
