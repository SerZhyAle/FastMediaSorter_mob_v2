package com.sza.fastmediasorter.util

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * S2292: Web Mercator tile addressing, in both directions.
 *
 * Extracted from `OsmMapTileProvider`, which needed only the forward direction - a position in, a
 * tile index out. The live Google Maps frame needs the inverse as well, because it publishes a
 * coordinate to a third party and must publish it at tile precision rather than at point precision.
 * Sharing one implementation is what makes "the same precision class as the static map gadget" a
 * property of the code instead of a coincidence two classes would have to keep re-establishing.
 */
object WebMercatorTile {

    /** Web Mercator, the tile addressing every OSM-compatible source shares. */
    fun tileX(longitude: Double, zoom: Int): Int =
        clamp((longitude + HALF_TURN_DEGREES) / FULL_TURN_DEGREES * tileCount(zoom), zoom)

    fun tileY(latitude: Double, zoom: Int): Int {
        val radians = latitude * PI / HALF_TURN_DEGREES
        val projected = asinh(tan(radians)) / PI
        return clamp((1.0 - projected) / 2.0 * tileCount(zoom), zoom)
    }

    fun centreLongitude(tileX: Int, zoom: Int): Double =
        (tileX + HALF_TILE) / tileCount(zoom) * FULL_TURN_DEGREES - HALF_TURN_DEGREES

    fun centreLatitude(tileY: Int, zoom: Int): Double {
        val projected = PI * (1.0 - 2.0 * (tileY + HALF_TILE) / tileCount(zoom))
        return atan(sinh(projected)) * HALF_TURN_DEGREES / PI
    }

    /**
     * The centre of the tile that contains [longitude]. Every position inside one tile answers with
     * the same value, which is the whole point: the answer says which tile the caller is in and
     * nothing finer.
     */
    fun coarseLongitude(longitude: Double, zoom: Int): Double =
        centreLongitude(tileX(longitude, zoom), zoom)

    /** Latitude counterpart of [coarseLongitude]. */
    fun coarseLatitude(latitude: Double, zoom: Int): Double =
        centreLatitude(tileY(latitude, zoom), zoom)

    /** The antimeridian and the poles land exactly on the edge, where the index is one past the last. */
    private fun clamp(raw: Double, zoom: Int): Int =
        floor(raw).toInt().coerceIn(0, tileCount(zoom).toInt() - 1)

    private fun tileCount(zoom: Int): Double = 2.0.pow(zoom)

    private const val HALF_TURN_DEGREES = 180.0
    private const val FULL_TURN_DEGREES = 360.0
    private const val HALF_TILE = 0.5
}
