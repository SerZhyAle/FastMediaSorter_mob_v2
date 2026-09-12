package com.sza.fastmediasorter.wear.domain.capability

import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode

/**
 * S2773: what a build variant says about screen geometry before the user has said anything.
 *
 * Two questions, and they are separate on purpose: which view a fresh install starts on, and whether
 * this build hands the user a control to change it. The published variant answers the first and
 * refuses the second, because a control that restores the shape the review rejected returns a settled
 * claim to anyone who opens the settings (strategic ADR-3).
 *
 * Implementations live one per flavor source set with a `@Binds` module beside each
 * (`dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8). There is deliberately no default in shared code: two
 * same-named declarations across `main` and a flavor set diverge silently, and a `BuildConfig.IS_*`
 * check here is refused outright (CLAUDE.md Rule 14).
 */
interface WearGeometryDefaults {

    /**
     * The view a fresh install lays out with. Overridden by a stored user choice where one exists, so
     * this answers "what does this build consider its starting view", never "what is in force now".
     */
    val startingMode: WearGeometryMode

    /**
     * Whether this build shows the row that switches the view. False withholds the control without
     * withholding the view: [startingMode] still decides what the build draws.
     */
    val offersModeSwitch: Boolean
}
