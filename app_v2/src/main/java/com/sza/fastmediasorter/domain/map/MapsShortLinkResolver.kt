package com.sza.fastmediasorter.domain.map

/** A point on the map, as carried by a shared map link. */
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * S1585: turns a shared Google Maps link into the point it denotes.
 *
 * A short link is the most precise identifier a share carries, but it names the place only
 * indirectly, so it has to be resolved before a route can be built from it. Resolution is a seam
 * rather than a helper because the way a link maps to a point is external and changeable: today it
 * is a redirect unwrap, tomorrow it may be another source (strategic 5.3).
 *
 * A null result means "point unknown", not "error": the caller degrades to showing the place rather
 * than dropping the share (strategic 6.1). Implementations therefore never throw for an unreachable
 * network, an unexpected payload, or an unsupported link.
 */
interface MapsShortLinkResolver {

    suspend fun resolve(link: String): MapPoint?
}
