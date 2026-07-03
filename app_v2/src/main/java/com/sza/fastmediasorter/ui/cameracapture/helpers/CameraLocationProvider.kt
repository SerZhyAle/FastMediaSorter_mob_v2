package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import timber.log.Timber

/**
 * S0766: dependency-free location source for the opt-in camera geotag. The host warms it via [start]
 * while the camera is open and stops it via [stop] (symmetric, lifecycle-bound); [lastKnownLocation]
 * is a plain field read on the capture path, so the shutter is never blocked waiting for a fix.
 *
 * Platform [LocationManager] only (no GMS), works on minSdk 23. The caller guarantees the location
 * permission grant before [start]; every system call is wrapped so a capture can never crash on a
 * location error. The four-method [LocationListener] is implemented explicitly (not a SAM lambda):
 * the interface's default methods only exist from API 30, so on minSdk-23 devices a SAM proxy would
 * miss the other callbacks and throw at runtime.
 */
class CameraLocationProvider {

    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = keepFreshest(location)

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (locationManager != null) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = manager
        CANDIDATE_PROVIDERS.forEach { provider ->
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) return@forEach
            runCatching {
                manager.getLastKnownLocation(provider)?.let { keepFreshest(it) }
                manager.requestLocationUpdates(
                    provider,
                    MIN_UPDATE_INTERVAL_MS,
                    MIN_UPDATE_DISTANCE_M,
                    listener,
                    Looper.getMainLooper(),
                )
            }.onFailure { Timber.w(it, "CameraLocationProvider: cannot warm provider %s", provider) }
        }
    }

    /** Freshest cached fix, or null when the source is not warmed or no fix has arrived. */
    fun lastKnownLocation(): Location? = lastLocation

    fun stop() {
        locationManager?.let { manager ->
            runCatching { manager.removeUpdates(listener) }
                .onFailure { Timber.w(it, "CameraLocationProvider: removeUpdates failed") }
        }
        locationManager = null
        lastLocation = null
    }

    /** Keeps the most recent fix by timestamp so a fresh GPS fix is not overwritten by a stale one. */
    private fun keepFreshest(candidate: Location) {
        val current = lastLocation
        if (current == null || candidate.time >= current.time) lastLocation = candidate
    }

    companion object {
        private val CANDIDATE_PROVIDERS =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        private const val MIN_UPDATE_INTERVAL_MS = 2000L
        private const val MIN_UPDATE_DISTANCE_M = 0f
    }
}
