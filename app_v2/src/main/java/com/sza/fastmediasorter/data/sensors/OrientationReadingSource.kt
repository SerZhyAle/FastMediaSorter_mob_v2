package com.sza.fastmediasorter.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sza.fastmediasorter.domain.model.sensors.CompassReading
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1179: device heading for the compass tile.
 *
 * Cold by construction: the listener is registered when a collector arrives and unregistered in
 * `awaitClose` when the last one leaves. Writing the pair anywhere else is what strategic §3.2
 * forbids and what `assert-listener-symmetry` refuses - and §7 names a subscription outliving the
 * desktop as this feature's most likely battery defect.
 */
@Singleton
class OrientationReadingSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun readings(): Flow<CompassReading> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // One return rather than a guard-return per condition: detekt caps a function at two
        // (ReturnCount), and this shape recurs in every source of this ticket.
        return if (manager == null || sensor == null) {
            emptyFlow()
        } else {
            callbackFlow {
                // Reused across events on purpose: both callbacks arrive on one handler thread,
                // and a fresh pair of arrays per sample would allocate on every sensor tick.
                val rotationMatrix = FloatArray(ROTATION_MATRIX_SIZE)
                val orientation = FloatArray(ORIENTATION_SIZE)
                var accuracy = SensorAccuracy.UNRELIABLE

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        trySend(
                            CompassReading(
                                azimuthDegrees = toBearing(orientation[AZIMUTH_INDEX]),
                                accuracy = accuracy,
                                // Altitude belongs to the position fix, not to orientation - the
                                // compass tile combines the two streams itself.
                                altitudeMeters = null,
                                takenAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }

                    override fun onAccuracyChanged(changed: Sensor?, status: Int) {
                        accuracy = SensorAccuracy.fromPlatformStatus(status)
                    }
                }

                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                awaitClose { manager.unregisterListener(listener) }
            }.conflate()
        }
    }

    /** Platform azimuth is radians in -PI..PI; a tile wants degrees in 0..360. */
    private fun toBearing(azimuthRadians: Float): Float {
        val degrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
        return (degrees + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    }

    private companion object {
        const val ROTATION_MATRIX_SIZE = 9
        const val ORIENTATION_SIZE = 3
        const val AZIMUTH_INDEX = 0
        const val FULL_CIRCLE_DEGREES = 360f
    }
}
