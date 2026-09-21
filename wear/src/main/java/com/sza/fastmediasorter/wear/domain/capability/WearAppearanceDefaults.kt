package com.sza.fastmediasorter.wear.domain.capability

import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode

/**
 * S3362: what a build variant draws behind its screens before the user has said anything.
 *
 * A starting value, never a restriction: both flavors keep the whole background list in the screen
 * settings, and a stored choice always wins over this answer. Wear OS review item WO-V13 asks for a
 * black background, and the flavor Play distributes is the one that has to satisfy it on the frame a
 * reviewer sees first - which is the frame nobody has chosen anything for yet.
 *
 * Implementations live one per flavor source set with a `@Binds` module beside each
 * (`dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8), in the shape [WearGeometryDefaults] already uses for
 * the same kind of question. There is deliberately no default in shared code: two same-named
 * declarations across `main` and a flavor set diverge silently, and a `BuildConfig.IS_*` check here
 * is refused outright (CLAUDE.md Rule 14).
 */
interface WearAppearanceDefaults {

    /**
     * The background a watch draws while nothing is stored for it. Overridden by a stored user choice
     * wherever one exists, so this answers "what does this build start with", never "what is in force".
     */
    val startingBackgroundMode: WearBackgroundMode
}
