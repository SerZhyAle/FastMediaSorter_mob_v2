package com.sza.fastmediasorter.wear.ui.home

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearContentType

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
        // S2509: the share glyph, not ic_cast. Cast is already the channel glyph on two rows of this
        // very screen, and a broadcast is the opposite direction - what this watch sends out.
        HomeSectionId.BROADCAST -> R.drawable.ic_share
        // S2551: the camera glyph, not ic_share. This row is the opposite direction of the one above -
        // something is watched here rather than sent out - and the entity it stands for is a camera.
        HomeSectionId.PHONE_CAMERA -> R.drawable.ic_camera_capture
        // S3116: the Apps glyph is the fallback nothing should reach - this row carries the program it
        // stands for and is drawn from that program's own glyph. It answers here for the caller that
        // has the section id alone, and says the only true thing available then: a program of the
        // Apps list is behind it.
        HomeSectionId.LAST_USED_APP -> R.drawable.ic_apps
    }

    /**
     * Which content type a home section stands for, or null when it stands for none.
     *
     * The home screen lists origins, not content types, so only streams names one outright. The rest
     * take the catalog's `OTHER` tone, which is the umbrella the catalog already documents for "a
     * source registered in this app" - the same reading that gave the Resources section its glyph.
     *
     * Favourites is null deliberately: `ic_resource_favorites` is a fixed amber badge with no tint
     * hook, so a semantic tone would repaint the star (strategic §11 criterion 7).
     *
     * S3434: lifted out of `HomeScreen.kt` for the same reason as the glyph table above - the sections
     * tile paints its plates from this answer, and a second copy would let a tile cell and its home row
     * wear two hues.
     */
    fun contentTypeFor(id: HomeSectionId): WearContentType? = when (id) {
        HomeSectionId.FAVOURITES -> null
        // S2499: a recent channel is a channel, so it takes the same tone the Streams section does.
        HomeSectionId.STREAMS,
        HomeSectionId.LAST_USED_STREAM -> WearContentType.STREAM
        HomeSectionId.LAST_USED_RESOURCE,
        HomeSectionId.RESOURCES,
        HomeSectionId.PHONE,
        HomeSectionId.LOCAL,
        // S2509: OTHER rather than STREAM. This row is a program of this app, not a channel registered
        // in it - giving it the stream tone would say the watch has a channel to play.
        HomeSectionId.BROADCAST,
        // S2551: OTHER for the same reason as the row above. What this one opens IS a stream, but it is
        // one that exists only while the session does - giving it the stream tone would place it beside
        // the registered channels, which is exactly what the ticket's non-goal keeps it out of.
        HomeSectionId.PHONE_CAMERA,
        // S3116: never reached while the row carries its program - the accent answers first - and
        // OTHER when it does not, for the same reason as the Programs row: it is a program of this app.
        HomeSectionId.LAST_USED_APP,
        HomeSectionId.APPS -> WearContentType.OTHER
    }
}
