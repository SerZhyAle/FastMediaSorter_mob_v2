package com.sza.fastmediasorter.domain.model.map

import java.util.Locale

/** A geographic position, kept free of `android.location.Location` so the domain stays testable. */
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
) {

    /**
     * The caption fallback when no address is available - the gadget must never show an empty label.
     *
     * [Locale.US] is not a slip: a comma-decimal locale would render the pair as `55,7558, 37,6173`,
     * three commas with no way to tell the two numbers apart.
     */
    fun formatted(): String = String.format(Locale.US, COORDINATE_FORMAT, latitude, longitude)

    private companion object {
        const val COORDINATE_FORMAT = "%.4f, %.4f"
    }
}

/**
 * S1175: what the map gadget draws.
 *
 * [imagePath] is a local file rather than a bitmap: the domain must not own decoded pixels, and the
 * tile is on disk anyway because the OSM tile policy requires caching it.
 */
data class MapSnapshot(
    val point: MapPoint,
    val label: String,
    val imagePath: String,
    val attribution: String,
    val capturedAt: Long,
)
