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
    FAVOURITES,

    /**
     * S2511: a grid of the watch's mini-programs.
     *
     * Carries no assignment at all, so the sentence above about storing per kind does not reach it: there
     * is nothing to store, because adding the tile to the carousel is itself the act of pinning and the
     * grid's contents come from [com.sza.fastmediasorter.wear.domain.catalog.WearAppCatalog].
     */
    PROGRAMS,

    /** S2511: a grid of the app's home sections. Carries no assignment, for [PROGRAMS]'s reason. */
    SECTIONS
}

/**
 * Whether a target can be chosen for this kind at all.
 *
 * S2511: false is not "nothing has been chosen yet" - it is "there is nothing here to choose", and the two
 * answers lead to opposite screens. A kind that answers false must never reach the target picker, which
 * would open an empty chooser from a tile that is already working.
 *
 * Exhaustive with no else branch on purpose: a new kind is classified here or it does not compile.
 */
val WearTileKind.carriesAssignableTarget: Boolean
    get() = when (this) {
        WearTileKind.RESOURCE, WearTileKind.STREAM -> true
        WearTileKind.FAVOURITES, WearTileKind.PROGRAMS, WearTileKind.SECTIONS -> false
    }
