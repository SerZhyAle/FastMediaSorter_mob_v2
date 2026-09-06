package com.sza.fastmediasorter.wear.ui.home

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId

/**
 * The single place on the watch that knows which glyph a home section wears.
 *
 * S2511 lifted this table out of `HomeScreen.kt`, where it was private to the composable file: a tile is
 * built outside Compose and outside the app process, so it could not reach it there, and a second copy
 * would be free to drift from the one the screen draws.
 *
 * Icons live here rather than on the section model so the domain layer carries no Compose or resource
 * concern. These are the phone's own vectors, copied into this module: one entity wears one glyph across
 * both apps, and `docs/ICON_LEGEND.md` is the table that decides which (owner instruction 2026-08-18).
 *
 * Exhaustive with no else branch on purpose: a new section must fail compilation here rather than silently
 * inherit some default glyph.
 */
object HomeSectionIconCatalog {

    @DrawableRes
    fun iconFor(id: HomeSectionId): Int = when (id) {
        HomeSectionId.LAST_USED_RESOURCE -> R.drawable.ic_history
        // S2499: the streams glyph rather than the history one - one entity wears one glyph, and the
        // history glyph is exactly what would make a recent channel indistinguishable from a folder.
        HomeSectionId.LAST_USED_STREAM -> R.drawable.ic_cast
        HomeSectionId.FAVOURITES -> R.drawable.ic_resource_favorites
        // ic_resource is the phone's canonical umbrella glyph for "a source registered in this app",
        // which is what this section lists. ic_wifi described the transport, not the entity (S1952).
        HomeSectionId.RESOURCES -> R.drawable.ic_resource
        HomeSectionId.PHONE -> R.drawable.ic_profile_personal_smartphone
        // Local means the watch's own storage, so it takes the phone's glyph for the watch - the phone's
        // "local storage" icon is a smartphone and would have been indistinguishable from PHONE above.
        HomeSectionId.LOCAL -> R.drawable.ic_watch
        HomeSectionId.STREAMS -> R.drawable.ic_cast
        HomeSectionId.APPS -> R.drawable.ic_apps
    }
}
