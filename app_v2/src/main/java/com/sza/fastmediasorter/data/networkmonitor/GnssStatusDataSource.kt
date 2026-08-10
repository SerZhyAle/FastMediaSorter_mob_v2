package com.sza.fastmediasorter.data.networkmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssConstellation
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssCoordinate
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssSatellite
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1433: observes the satellite constellation and the current position while the GNSS section is open.
 *
 * Cold and foreground-only. The callback and the location request are made when a section collects and are
 * both dropped when it stops, which is the whole of the "no background location" guarantee - there is no
 * service, no pending intent and no passive listener anywhere in this class, so nothing can outlive the
 * screen even if a caller forgets to cancel.
 *
 * Applies no `flowOn`, matching every other Monitor data source: the dispatcher belongs to the consumer.
 */
@Singleton
class GnssStatusDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /**
     * Emits a snapshot per satellite or position update, or one explanation of why there is none.
     *
     * The unavailable case is a single-value flow rather than an empty one, so a section that shows the
     * reason gets it immediately instead of waiting for an update that will never arrive.
     */
    fun observe(): Flow<MonitorSection<GnssSnapshot>> {
        val manager = locationManager
        val blocker = unavailableReason(manager)
        return if (manager != null && blocker == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            observeStatus(manager)
        } else {
            flowOf(MonitorSection.absent(blocker ?: SectionAvailability.NoHardware))
        }
    }

    /**
     * Why this device can show nothing, or null when it can.
     *
     * A disabled GPS provider is reported as [SectionAvailability.NoHardware] alongside a missing one: both
     * mean no permission and no waiting will produce a satellite, and the section says so rather than drawing
     * an empty sky the user would read as "no satellites overhead".
     */
    private fun unavailableReason(manager: LocationManager?): SectionAvailability? = when {
        manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> SectionAvailability.NoHardware
        !hasFineLocation() -> SectionAvailability.NoPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        !isGpsUsable(manager) -> SectionAvailability.NoHardware
        else -> null
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun observeStatus(manager: LocationManager): Flow<MonitorSection<GnssSnapshot>> = callbackFlow {
        val state = GnssState()
        val handler = Handler(Looper.getMainLooper())

        val statusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                state.satellites = status.toSatellites()
                trySend(MonitorSection.available(state.snapshot()))
            }

            override fun onStopped() {
                state.satellites = emptyList()
                trySend(MonitorSection.available(state.snapshot()))
            }
        }
        val locationListener = positionListener { location ->
            state.coordinate = location.toCoordinate()
            trySend(MonitorSection.available(state.snapshot()))
        }

        val refusal = register(manager, statusCallback, locationListener, handler)
        trySend(
            if (refusal == null) {
                MonitorSection.available(state.snapshot())
            } else {
                MonitorSection.absent(refusal)
            }
        )

        awaitClose {
            if (refusal == null) {
                unregister(manager, statusCallback, locationListener)
            }
        }
    }

    /**
     * Registers both sources, or reports that the platform refused.
     *
     * Two different refusals, both handled. A `SecurityException` is possible even with the grant recorded -
     * the user can revoke it between the check and this call - and is caught rather than allowed to escape
     * the flow builder, which would skip `awaitClose` and leave whichever source did register attached for
     * the life of the process. The quieter one is `registerGnssStatusCallback` returning false: the receiver
     * declined without throwing, and treating that as success would leave the section showing an empty sky
     * forever instead of saying the hardware refused.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun register(
        manager: LocationManager,
        statusCallback: GnssStatus.Callback,
        locationListener: LocationListener,
        handler: Handler,
    ): SectionAvailability? = try {
        if (manager.registerGnssStatusCallback(statusCallback, handler)) {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_INTERVAL_MS,
                MIN_UPDATE_DISTANCE_METERS,
                locationListener,
                Looper.getMainLooper(),
            )
            null
        } else {
            Timber.w("GNSS status callback declined by the receiver")
            SectionAvailability.NoHardware
        }
    } catch (security: SecurityException) {
        Timber.w(security, "GNSS observation refused despite a granted permission")
        manager.unregisterGnssStatusCallback(statusCallback)
        SectionAvailability.NoPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun unregister(
        manager: LocationManager,
        statusCallback: GnssStatus.Callback,
        locationListener: LocationListener,
    ) {
        manager.unregisterGnssStatusCallback(statusCallback)
        manager.removeUpdates(locationListener)
    }

    /**
     * The position sink.
     *
     * Every member is overridden rather than relying on the interface defaults, which only exist from API 30:
     * a listener that leaves them out is called into on an older platform and dies with `AbstractMethodError`.
     */
    @Suppress("DEPRECATION")
    private fun positionListener(onLocation: (Location) -> Unit): LocationListener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location)

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isGpsUsable(manager: LocationManager): Boolean =
        LocationManager.GPS_PROVIDER in manager.allProviders &&
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    /** Mutable view of the two sources, mutated only on the main looper both callbacks are posted to. */
    private class GnssState {

        var satellites: List<GnssSatellite> = emptyList()
        var coordinate: GnssCoordinate? = null

        fun snapshot(): GnssSnapshot = GnssSnapshot(
            satellites = satellites,
            coordinate = coordinate,
            coordinateAvailability = if (coordinate == null) {
                SectionAvailability.NoNetwork
            } else {
                SectionAvailability.Available
            },
            takenAtMillis = System.currentTimeMillis(),
        )
    }

    private companion object {

        /** Position updates are for a diagnostic readout, not navigation - once a second is plenty. */
        const val MIN_UPDATE_INTERVAL_MS = 1000L

        /** Every update is wanted, including a stationary one: a fresh fix time is itself the diagnostic. */
        const val MIN_UPDATE_DISTANCE_METERS = 0f
    }
}

/** The platform reports an undecoded satellite as this, not as an absent measurement. */
private const val CN0_UNREPORTED = 0f

@RequiresApi(Build.VERSION_CODES.N)
private fun GnssStatus.toSatellites(): List<GnssSatellite> = (0 until satelliteCount).map { index ->
    GnssSatellite(
        constellation = constellationOf(getConstellationType(index)),
        satelliteId = getSvid(index),
        cn0DbHz = getCn0DbHz(index).takeIf { it > CN0_UNREPORTED }?.toDouble(),
        usedInFix = usedInFix(index),
        elevationDegrees = getElevationDegrees(index),
        azimuthDegrees = getAzimuthDegrees(index),
    )
}

@RequiresApi(Build.VERSION_CODES.N)
private fun constellationOf(type: Int): GnssConstellation = when (type) {
    GnssStatus.CONSTELLATION_GPS -> GnssConstellation.GPS
    GnssStatus.CONSTELLATION_GLONASS -> GnssConstellation.GLONASS
    GnssStatus.CONSTELLATION_GALILEO -> GnssConstellation.GALILEO
    GnssStatus.CONSTELLATION_BEIDOU -> GnssConstellation.BEIDOU
    GnssStatus.CONSTELLATION_QZSS -> GnssConstellation.QZSS
    GnssStatus.CONSTELLATION_SBAS -> GnssConstellation.SBAS
    else -> irnssOrUnknown(type)
}

/** IRNSS/NavIC only became a named constellation in API 29; below that it arrives as an unknown type. */
private fun irnssOrUnknown(type: Int): GnssConstellation =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == GnssStatus.CONSTELLATION_IRNSS) {
        GnssConstellation.IRNSS
    } else {
        GnssConstellation.UNKNOWN
    }

private fun Location.toCoordinate(): GnssCoordinate = GnssCoordinate(
    latitudeDegrees = latitude,
    longitudeDegrees = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    fixedAtMillis = time,
)
