package com.sza.fastmediasorter.data.map

import android.content.Context
import android.location.Geocoder
import com.sza.fastmediasorter.domain.map.MapPlaceLabelProvider
import com.sza.fastmediasorter.domain.model.map.MapPoint
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
        // Broad on purpose, and the interface says so: the geocoder is an OEM system service that
        // answers with IllegalArgumentException on odd coordinates and DeadObjectException when it
        // dies, and a caption is never worth taking the home screen down for. Safe default plus a log.
        runCatching { geocoder.getFromLocation(point.latitude, point.longitude, MAX_RESULTS) }
            .onFailure { error -> Timber.w(error, "MapLabel: geocoder unavailable") }
            .getOrNull()
            ?.firstOrNull()
            ?.let { address ->
                address.getAddressLine(0)
                    ?: listOfNotNull(address.locality, address.countryName)
                        .joinToString(", ")
                        .ifBlank { null }
            }
    }

    private companion object {
        const val MAX_RESULTS = 1
    }
}
