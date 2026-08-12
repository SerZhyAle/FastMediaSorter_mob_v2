package com.sza.fastmediasorter.domain.location

import com.sza.fastmediasorter.domain.model.map.MapPoint

/**
 * S1175: the one place that reads the device position for a launcher surface.
 *
 * Separate from [com.sza.fastmediasorter.data.sensors.MotionReadingSource], which exposes speed and
 * altitude but no coordinates, and bound behind this seam rather than reused from the camera helper
 * so a launcher cell never depends on a UI package.
 *
 * Neither method throws: the home screen must survive a revoked permission or a silent provider, so
 * a failure comes back as `null` and the repository decides what the user sees.
 */
interface DeviceLocationSource {

    /** False when the user has not granted a location permission, which is a displayable state. */
    fun hasPermission(): Boolean

    /** Null when there is no permission, no enabled provider, or no fix the platform will hand over. */
    suspend fun lastKnownPoint(): MapPoint?
}
