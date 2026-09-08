package com.sza.fastmediasorter.wear.domain.model

/**
 * S2751: what an outside caller asked to open, described without naming a navigation address.
 *
 * The resolver reads the stores and answers with one of these; turning the answer into a route is the
 * navigation branch's job. Keeping the two apart is what stops the domain from importing the screens:
 * a resolver that returned a route string would have to know the route table, and that table is
 * presentation (strategic ADR-2).
 *
 * A target that no longer resolves is a null answer rather than a member here - "the thing you pinned is
 * gone" and "here is a screen" are different answers and the caller has to be able to tell them apart.
 */
sealed interface WearLaunchAddress {

    /** A fixed screen, named by the destination the shortcut grid and the tiles already address. */
    data class Screen(val destination: WearDestinationId) : WearLaunchAddress

    /** The screen that assigns a target to a tile of the given kind. */
    data class TileTargetPicker(val kind: WearTileKind) : WearLaunchAddress

    /** The overview of a registered source, which re-reads the store from this id on arrival. */
    data class SourceOverview(val sourceId: String, val sourceName: String) : WearLaunchAddress

    /**
     * A prepared channel, with the player already chosen.
     *
     * [isVideo] rather than a mime type on purpose: playback preparation is what hands out [fileId], and
     * it reports this flag and no mime type at all. Folding this into [MediaFile] would leave the player
     * to be guessed from a type nobody here holds, which is a different answer for a pinned channel.
     */
    data class StreamPlayback(val fileId: Long, val isVideo: Boolean) : WearLaunchAddress

    /** One prepared file, whose own type and name decide which player renders it. */
    data class MediaFile(
        val fileId: Long,
        val mimeType: String?,
        val fileName: String? = null
    ) : WearLaunchAddress
}
