package com.sza.fastmediasorter.wear.data.motion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import com.sza.fastmediasorter.wear.domain.motion.WearSensorAvailability
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamId
import com.sza.fastmediasorter.wear.util.getPackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether this build may read a step stream at all, and says why when it may not.
 *
 * S2458 ADR-3: the answer comes from the app's own MERGED manifest, never from a flavor check - Rule 14
 * bans one in shared code and the `wear` module declares no `buildConfigField` to check anyway. The
 * manifest is already the truth here, and it stays the truth if the permission ever moves editions.
 *
 * The API 29 fork is not a nicety. ACTIVITY_RECOGNITION only became a runtime permission there, and this
 * module's floor is API 28, where `checkSelfPermission` answers about a permission the platform does not
 * know - a declared permission would read back as denied and the section would explain a refusal that
 * never happened.
 */
@Singleton
class WearActivityRecognitionState @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Constant for the life of the process: a manifest cannot change under a running app. */
    private val declared: Boolean by lazy { declaresActivityRecognition() }

    /**
     * Resolves one activity stream. A motion id has no business here - it needs no permission - and is
     * answered [WearSensorAvailability.NoHardware] rather than silently treated as available.
     */
    fun resolve(streamId: WearSensorStreamId): WearSensorAvailability {
        val sensorType = ACTIVITY_SENSOR_TYPES[streamId]
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorType?.let { manager?.getDefaultSensor(it) }
        val availability = when {
            sensor == null -> WearSensorAvailability.NoHardware
            !declared -> WearSensorAvailability.NotInThisEdition
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> WearSensorAvailability.Available
            !isGranted() -> WearSensorAvailability.PermissionDenied
            else -> WearSensorAvailability.Available
        }
        return availability
    }

    private fun declaresActivityRecognition(): Boolean = try {
        context.packageManager
            .getPackageInfoCompat(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(Manifest.permission.ACTIVITY_RECOGNITION) == true
    } catch (e: PackageManager.NameNotFoundException) {
        // The platform failing to describe the app to itself is not a reason to claim a capability:
        // withholding the step section is the safe answer, and the section explains itself either way.
        Timber.w(e, "Own package info unavailable; treating activity recognition as undeclared")
        false
    }

    private fun isGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        val ACTIVITY_SENSOR_TYPES = mapOf(
            WearSensorStreamId.STEP_COUNTER to Sensor.TYPE_STEP_COUNTER,
            WearSensorStreamId.STEP_DETECTOR to Sensor.TYPE_STEP_DETECTOR
        )
    }
}
