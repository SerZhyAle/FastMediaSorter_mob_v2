package com.sza.fastmediasorter.data.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.sza.fastmediasorter.domain.model.sensors.MotionReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1179: one position stream feeding the speed tile, the altitude tile and both charts.
 *
 * One source rather than one per tile, so two gadgets reading at the same moment cannot disagree
 * about the same fix (strategic ADR-1). Platform [LocationManager] only, no GMS, mirroring
 * `CameraLocationProvider` (S0766) - the shape that already works on this project's minSdk.
 *
 * Updates are requested when a collector arrives and removed in `awaitClose`. Nothing here runs in
 * the background: strategic §6 item 5 and the §2 non-goals rule that out, because background
 * collection would pull in a background-location permission and a separate Play declaration.
 *
 * The caller guarantees the location grant before collecting, exactly as `CameraLocationProvider`
 * does; every platform call is wrapped so a provider that refuses cannot take the desktop down.
 */
@Singleton
class MotionReadingSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @SuppressLint("MissingPermission")
    fun readings(): Flow<MotionReading> {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return emptyFlow()

        return callbackFlow {
            var previous: Location? = null

            // The four-method interface is implemented explicitly rather than as a SAM lambda: the
            // default methods only exist from API 30, so on a minSdk-23 device a SAM proxy misses
            // the other callbacks and throws at run time (same reason as CameraLocationProvider).
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val delta = distanceSince(previous, location)
                    previous = location
                    trySend(
                        MotionReading.fromPlatformSpeed(
                            rawSpeed = location.speed.takeIf { location.hasSpeed() },
                            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
                            distanceDeltaMeters = delta,
                            takenAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit
            }

            CANDIDATE_PROVIDERS.forEach { provider ->
                if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                    return@forEach
                }
                runCatching {
                    manager.requestLocationUpdates(
                        provider,
                        MIN_UPDATE_INTERVAL_MS,
                        MIN_UPDATE_DISTANCE_M,
                        listener,
                        Looper.getMainLooper(),
                    )
                }.onFailure {
                    Timber.w(it, "MotionReadingSource: cannot warm provider %s", provider)
                }
            }

            awaitClose {
                runCatching { manager.removeUpdates(listener) }
                    .onFailure { Timber.w(it, "MotionReadingSource: removeUpdates failed") }
            }
        }.conflate()
    }

    /**
     * Zero for the first fix, and zero for a fix too coarse to trust: a parked phone drifting
     * between cell-tower fixes would otherwise accumulate kilometres of trip distance overnight,
     * which strategic §7 names as the failure that makes the trip meter untrustworthy.
     */
    private fun distanceSince(previous: Location?, current: Location): Double = when {
        previous == null -> 0.0
        current.hasAccuracy() && current.accuracy > MAX_TRUSTED_ACCURACY_M -> 0.0
        else -> previous.distanceTo(current).toDouble()
    }

    private companion object {
        val CANDIDATE_PROVIDERS =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val MIN_UPDATE_INTERVAL_MS = 2000L
        const val MIN_UPDATE_DISTANCE_M = 0f
        const val MAX_TRUSTED_ACCURACY_M = 50f
    }
}
