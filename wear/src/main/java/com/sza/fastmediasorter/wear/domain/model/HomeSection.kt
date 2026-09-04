package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes

/** Sections of the watch home screen, declared in the fixed order the owner specified. */
enum class HomeSectionId {
    LAST_USED_RESOURCE,

    /** S2499: a recently played channel, which wears the streams glyph rather than the history one. */
    LAST_USED_STREAM,
    RESOURCES,
    PHONE,
    LOCAL,
    STREAMS,
    APPS,
    FAVOURITES
}

/**
 * One row of the home screen.
 *
 * [dynamicLabel] wins over [labelRes] for the last-used resource, which is shown under its own name
 * rather than a generic caption. [iconId] does the same for the glyph, and only the last-used
 * entries ever carry one - every other section keeps the fixed glyph its id names.
 *
 * S2499: a section carries either a [route] it can be navigated to as it stands, or a [targetRef] that
 * has to be resolved into one first - never neither. Only the last-used row ever takes the second form,
 * and only for a channel: a player address contains a number handed out by playback preparation, which
 * has not run on a cold start, so it cannot be built ahead of the tap.
 */
data class HomeSection(
    val id: HomeSectionId,
    @StringRes val labelRes: Int,
    val route: String?,
    val dynamicLabel: String? = null,
    val iconId: String? = null,
    val targetRef: WearTileTargetRef? = null
)

/**
 * Everything the catalog needs to decide visibility, reduced to plain values so the catalog stays
 * free of repository dependencies and can be reasoned about without a coroutine in sight.
 *
 * S1974: the last-used shortcuts are deliberately absent. They are no longer catalog members, because
 * a conditional member of the chunked list is exactly what used to shift every predefined section by
 * one cell.
 */
data class HomeSectionVisibility(
    val streamsEnabled: Boolean
)
