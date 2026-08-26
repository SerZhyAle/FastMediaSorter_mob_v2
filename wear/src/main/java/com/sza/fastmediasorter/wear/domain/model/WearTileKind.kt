package com.sza.fastmediasorter.wear.domain.model

/**
 * S1955: the kinds of tile this watch app offers in the system carousel.
 *
 * A kind is what the user sees and adds to a screen, and it is also the key the tile assignment is stored
 * under. Storing per kind rather than per tile instance is deliberate (strategic ADR-3): whether the
 * platform keeps state per instance is unverified, and the first iteration must not depend on the answer.
 */
enum class WearTileKind {
    RESOURCE,
    STREAM,
    FAVOURITES
}
