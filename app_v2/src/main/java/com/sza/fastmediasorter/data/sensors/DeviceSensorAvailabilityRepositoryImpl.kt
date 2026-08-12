package com.sza.fastmediasorter.data.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.util.getPackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1179: resolves each capability once. Hardware does not appear or disappear while the process
 * lives, so re-asking the platform on every picker open would buy nothing.
 */
@Singleton
class DeviceSensorAvailabilityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorAvailabilityRepository {

    private val availability: Map<SensorCapability, Boolean> by lazy {
        SensorCapability.entries.associateWith { resolve(it) }
    }

    override fun isAvailable(capability: SensorCapability): Boolean =
        availability[capability] == true

    private fun resolve(capability: SensorCapability): Boolean = when (capability) {
        SensorCapability.COMPASS -> hasCompass()
        SensorCapability.LOCATION ->
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
        // Strategic §3.2 puts the steps gadget at API 29+, which is where ACTIVITY_RECOGNITION
        // became a runtime permission - below it the tile could read the counter without ever
        // asking, which is not the contract the permission row describes.
        // S1614: the permission is declared only by the flavors that ship the step counter, so a
        // build without it must report the capability absent rather than offer a tile whose
        // permission request the system would refuse outright.
        SensorCapability.STEP_COUNTER ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                declaresActivityRecognition() &&
                hasSensor(Sensor.TYPE_STEP_COUNTER)
    }

    /**
     * Reads the MERGED manifest rather than a build flag: the permission's presence is the fact the
     * platform acts on, and a flavor guard in shared code would only mirror it (CLAUDE.md Rule 14).
     */
    private fun declaresActivityRecognition(): Boolean =
        context.packageManager
            .getPackageInfoCompat(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(Manifest.permission.ACTIVITY_RECOGNITION) == true

    /**
     * The fused rotation vector is preferred; the raw accelerometer and magnetometer pair is the
     * fallback, because a device can ship the two physical sensors without the fused virtual one.
     */
    private fun hasCompass(): Boolean =
        hasSensor(Sensor.TYPE_ROTATION_VECTOR) ||
            (hasSensor(Sensor.TYPE_ACCELEROMETER) && hasSensor(Sensor.TYPE_MAGNETIC_FIELD))

    private fun hasSensor(type: Int): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(type) != null
}
