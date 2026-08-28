package com.sza.fastmediasorter.domain.model

/**
 * S2199: the sort and filters the resource list was last left in.
 *
 * Null in any field means "nothing remembered", so a caller keeps its own default instead of being
 * handed one this type invented. Typed rather than the raw stored strings: decoding a name that a
 * later release renamed is the store's problem, not the screen's.
 */
data class MainListSession(
    val sortMode: SortMode? = null,
    val filterByType: Set<ResourceType>? = null,
    val filterByMediaType: Set<MediaType>? = null,
    val filterByName: String? = null,
)
