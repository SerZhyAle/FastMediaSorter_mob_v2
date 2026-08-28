package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes

/** Sections of the watch home screen, declared in the fixed order the owner specified. */
enum class HomeSectionId {
    LAST_USED_RESOURCE,
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
 */
data class HomeSection(
    val id: HomeSectionId,
    @StringRes val labelRes: Int,
    val route: String,
    val dynamicLabel: String? = null,
    val iconId: String? = null
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
