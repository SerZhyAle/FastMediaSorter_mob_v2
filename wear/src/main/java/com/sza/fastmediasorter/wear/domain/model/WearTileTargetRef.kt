package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S1955: how a tile remembers what it points at, so the answer survives the death of the app process.
 *
 * A tile is built out of process and can be tapped when the app has never run, so it cannot address a
 * target by anything the running app happens to hold. The three kinds need three different answers
 * (strategic §6.4):
 *
 * - a favourite is already addressed by the pair the store keys off, so the tile addresses the whole list;
 * - a channel is addressed by its normalized URL, because a catalog re-import reassigns row ids;
 * - a resource has no durable id at all - the phone ships its own primary key and sync silently replaces
 *   the stored record, and its id with it, whenever a feature-tuple match wins. Only the tuple is durable.
 */
sealed interface WearTileTargetRef {

    /**
     * [id] is the fast path and [server]..[basePath] are the identity: after the phone's row is recreated
     * the id points at nothing, and the tuple is what still finds the same resource.
     */
    data class Resource(
        @SerializedName("id") val id: String,
        @SerializedName("type") val type: NetworkSourceType,
        @SerializedName("server") val server: String,
        @SerializedName("port") val port: Int,
        @SerializedName("shareName") val shareName: String?,
        @SerializedName("basePath") val basePath: String
    ) : WearTileTargetRef

    /** Built through [streamTargetRef] so an address is never stored in the shape the user typed it. */
    data class Stream(
        @SerializedName("normalizedUrl") val normalizedUrl: String
    ) : WearTileTargetRef

    /** The favourites list as a whole - there is nothing inside it for a tile to be assigned to. */
    data object Favourites : WearTileTargetRef
}

/** Normalizes on the way in, so two spellings of one address cannot become two different assignments. */
fun streamTargetRef(url: String): WearTileTargetRef.Stream =
    WearTileTargetRef.Stream(normalizeWearStreamUrl(url))

/**
 * The stored source [ref] names, or null when it is gone.
 *
 * Delegates to [NetworkSourceMerge.indexOfMatch] rather than repeating its comparison: that object is
 * already the one rule for "which stored source is this one", and a second copy here would be free to
 * drift from the rule sync actually applies.
 */
fun List<NetworkSource>.findByTargetRef(ref: WearTileTargetRef.Resource): NetworkSource? =
    getOrNull(NetworkSourceMerge.indexOfMatch(this, ref.toProbe()))

/**
 * A stand-in carrying only the fields the match reads; the credentials are deliberately blank because a
 * tile must never hold them.
 */
private fun WearTileTargetRef.Resource.toProbe(): NetworkSource = NetworkSource(
    id = id,
    type = type,
    name = "",
    server = server,
    port = port,
    username = "",
    password = "",
    shareName = shareName,
    basePath = basePath
)
