package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes

/** Sections of the watch home screen, declared in the fixed order the owner specified. */
enum class HomeSectionId {
    LAST_USED_RESOURCE,
    FAVOURITES,
    RESOURCES,
    PHONE,
    LOCAL,
    STREAMS,
    APPS
}

/**
 * One row of the home screen.
 *
 * [dynamicLabel] wins over [labelRes] for the last-used resource, which is shown under its own name
 * rather than a generic caption.
 */
data class HomeSection(
    val id: HomeSectionId,
    @StringRes val labelRes: Int,
    val route: String,
    val dynamicLabel: String? = null
)

/**
 * Everything the catalog needs to decide visibility, reduced to plain values so the catalog stays
 * free of repository dependencies and can be reasoned about without a coroutine in sight.
 */
data class HomeSectionVisibility(
    val favouritesEnabled: Boolean,
    val hasLastUsedResource: Boolean,
    val lastUsedResourceLabel: String?,
    val streamsEnabled: Boolean
)
