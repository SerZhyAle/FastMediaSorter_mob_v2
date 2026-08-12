package com.sza.fastmediasorter.domain.map

import com.sza.fastmediasorter.domain.model.map.MapPoint

/**
 * S1175: the one place that speaks to a map-tile service. Swapping the keyless source for another
 * must be a new implementation of this interface and nothing else (strategic ADR-2).
 *
 * [attribution] travels with the provider because the licence it satisfies belongs to the source,
 * not to the gadget - a swapped provider that kept the previous credit would be a licence breach.
 */
interface MapTileProvider {

    val attribution: String

    /** Local path of the cached tile image, or null on any failure - this method never throws. */
    suspend fun tileFor(point: MapPoint, zoom: Int): String?
}
