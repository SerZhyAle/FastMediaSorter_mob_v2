package com.sza.fastmediasorter.data.sensors

import android.hardware.GeomagneticField
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4145: the angle between magnetic and true north at a place, from the platform's world magnetic model.
 *
 * Lives in `data/` so the dashboard use case stays free of `android.hardware`.
 */
@Singleton
class MagneticDeclinationSource @Inject constructor() {

    fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, atMillis: Long): Float =
        GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            (altitudeMeters ?: 0.0).toFloat(),
            atMillis,
        ).declination
}
