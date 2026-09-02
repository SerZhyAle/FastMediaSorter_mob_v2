package com.sza.fastmediasorter.domain.model

/**
 * S2370: the result of rewriting one resource's favorite addresses after its own address changed
 * scheme (direct file path -> system folder tree). Counts, not identities: the caller reports the
 * total to the user, and no row is ever deleted by a remap.
 */
data class FavoritesRemapOutcome(
    val total: Int,
    val remapped: Int,
    val keptMissing: Int,
    val untouched: Int,
)
