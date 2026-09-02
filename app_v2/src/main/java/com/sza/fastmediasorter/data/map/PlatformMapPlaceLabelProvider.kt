package com.sza.fastmediasorter.data.map

import android.content.Context
import android.location.Geocoder
import com.sza.fastmediasorter.domain.map.MapPlaceLabelProvider
import com.sza.fastmediasorter.domain.model.map.MapPoint
import com.sza.fastmediasorter.util.WebMercatorTile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1175: turns the position into an address with the platform geocoder - keyless, so it stays inside
 * the "no paid or key-bearing services" rule.
 *
 * Many devices ship without a geocoder backend at all, which is why every failure degrades to null
 * and the caller shows coordinates instead.
 *
 * S2297: the geocoder is an OEM service that answers over the network, so it is asked about the
 * centre of the position's [OsmMapTileProvider.DEFAULT_ZOOM] tile and never about the position. That
 * is the very tile whose picture the gadget already downloads, so this round trip publishes nothing
 * the tile fetch has not published already - which is what makes the coarse-position answer in the
 * Data safety form a property of this code rather than a claim about someone else's backend.
 */
@Singleton
class PlatformMapPlaceLabelProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : MapPlaceLabelProvider {

    // Dispatchers.IO: the deprecated blocking overload is the only one available below API 33, and it
    // performs a network round trip.
    @Suppress("DEPRECATION")
    override suspend fun labelFor(point: MapPoint): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        val zoom = OsmMapTileProvider.DEFAULT_ZOOM
        val latitude = WebMercatorTile.coarseLatitude(point.latitude, zoom)
        val longitude = WebMercatorTile.coarseLongitude(point.longitude, zoom)
        Timber.d("S2297: geocoding tile centre $latitude,$longitude")
        // Broad on purpose, and the interface says so: the geocoder is an OEM system service that
        // answers with IllegalArgumentException on odd coordinates and DeadObjectException when it
        // dies, and a caption is never worth taking the home screen down for. Safe default plus a log.
        runCatching { geocoder.getFromLocation(latitude, longitude, MAX_RESULTS) }
            .onFailure { error -> Timber.w(error, "MapLabel: geocoder unavailable") }
            .getOrNull()
            ?.firstOrNull()
            ?.let { composeCoarseLabel(it.locality, it.subAdminArea, it.adminArea, it.countryName) }
    }

    private companion object {
        const val MAX_RESULTS = 1
    }
}

/**
 * S2297: the caption that matches a tile-centre query.
 *
 * The place part is the finest field that does not vary across one zoom-15 tile, which is why the
 * street line the geocoder also offers is unusable here: the tile centre lies hundreds of metres
 * from the real position, so that line would be precise in shape and false in substance - it would
 * name a street the user was never on. Falling through locality -> subAdminArea -> adminArea covers
 * the backends that leave the town field empty outside cities, where the district is all there is.
 */
internal fun composeCoarseLabel(
    locality: String?,
    subAdminArea: String?,
    adminArea: String?,
    countryName: String?,
): String? {
    val place = sequenceOf(locality, subAdminArea, adminArea).firstOrNull { !it.isNullOrBlank() }
    val country = countryName?.takeIf { it.isNotBlank() }
    return listOfNotNull(place, country).joinToString(", ").ifBlank { null }
}
