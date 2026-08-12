package com.sza.fastmediasorter.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.location.DeviceLocationSource
import com.sza.fastmediasorter.domain.model.map.MapPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1175: last-known position for a launcher cell, from the platform [LocationManager] only.
 *
 * It never subscribes to updates. A home-screen cell that armed a location listener would keep the
 * radio warm for as long as the desktop is on screen, which strategic §7 lists as the battery risk;
 * the freshest fix the platform already holds is enough for a map picture of the current area.
 */
@Singleton
class PlatformDeviceLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocationSource {

    override fun hasPermission(): Boolean = PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // Dispatchers.IO: getLastKnownLocation reads the platform's on-disk fix and blocks.
    @SuppressLint("MissingPermission") // hasPermission() gates every call below.
    override suspend fun lastKnownPoint(): MapPoint? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val freshest = CANDIDATE_PROVIDERS
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }
                    .onFailure { Timber.w(it, "DeviceLocation: provider %s refused a fix", provider) }
                    .getOrNull()
            }
            .maxByOrNull(Location::getTime)
        freshest?.let { MapPoint(it.latitude, it.longitude) }
    }

    private companion object {
        val PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        val CANDIDATE_PROVIDERS =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
