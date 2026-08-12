package com.sza.fastmediasorter.domain.map

import com.sza.fastmediasorter.domain.model.map.MapPoint

/**
 * S1175: turns a position into a human address.
 *
 * Deliberately separate from [MapTileProvider] (strategic §5.3): a device without a geocoder still
 * gets a map picture, and the caption falls back to coordinates instead of the whole gadget failing.
 */
interface MapPlaceLabelProvider {

    /** Null when no geocoder is present or it returns nothing; the caller then shows coordinates. */
    suspend fun labelFor(point: MapPoint): String?
}
