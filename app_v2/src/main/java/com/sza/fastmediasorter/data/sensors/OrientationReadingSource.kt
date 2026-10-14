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
        val rotation = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        // One return rather than a guard-return per condition: detekt caps a function at two
        // (ReturnCount), and this shape recurs in every source of this ticket.
        // S4149: the raw pair branch mirrors hasCompass in DeviceSensorAvailabilityRepositoryImpl -
        // the capability promises a heading without the fused sensor, so the source must deliver
        // one there too, or the tile is offered and never reads.
        return when {
            manager == null -> emptyFlow()
            rotation != null -> fusedReadings(manager, rotation)
            accelerometer != null && magnetometer != null ->
                rawPairReadings(manager, accelerometer, magnetometer)
            else -> emptyFlow()
        }
    }

    private fun fusedReadings(manager: SensorManager, sensor: Sensor): Flow<CompassReading> =
        callbackFlow {
            // Reused across events on purpose: both callbacks arrive on one handler thread,
            // and a fresh pair of arrays per sample would allocate on every sensor tick.
            val rotationMatrix = FloatArray(ROTATION_MATRIX_SIZE)
            val orientation = FloatArray(ORIENTATION_SIZE)
            var accuracy = SensorAccuracy.UNRELIABLE
            var lastEmittedAtMillis = 0L

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val now = System.currentTimeMillis()
                    if (now - lastEmittedAtMillis < MIN_UPDATE_INTERVAL_MS) return
                    lastEmittedAtMillis = now

                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    trySend(toReading(rotationMatrix, orientation, accuracy, now))
                }

                override fun onAccuracyChanged(changed: Sensor?, status: Int) {
                    accuracy = SensorAccuracy.fromPlatformStatus(status)
                }
            }

            manager.registerListener(listener, sensor, UPDATE_INTERVAL_MICROS)
            awaitClose { manager.unregisterListener(listener) }
        }.conflate()

    private fun rawPairReadings(
        manager: SensorManager,
        accelerometer: Sensor,
        magnetometer: Sensor,
    ): Flow<CompassReading> = callbackFlow {
        val gravity = FloatArray(VECTOR_SIZE)
        val geomagnetic = FloatArray(VECTOR_SIZE)
        val rotationMatrix = FloatArray(ROTATION_MATRIX_SIZE)
        val orientation = FloatArray(ORIENTATION_SIZE)
        var hasGravity = false
        var hasGeomagnetic = false
        var accuracy = SensorAccuracy.UNRELIABLE
        var lastEmittedAtMillis = 0L

        // One listener for both sensors: the single unregister in awaitClose drops the pair, so
        // neither registration can outlive the collector on its own.
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    event.values.copyInto(gravity, endIndex = VECTOR_SIZE)
                    hasGravity = true
                } else {
                    event.values.copyInto(geomagnetic, endIndex = VECTOR_SIZE)
                    hasGeomagnetic = true
                }
                val now = System.currentTimeMillis()
                val due = hasGravity && hasGeomagnetic &&
                    now - lastEmittedAtMillis >= MIN_UPDATE_INTERVAL_MS
                // getRotationMatrix refuses in free fall or in a field too weak to orient by;
                // skipping that sample beats emitting a heading computed from garbage.
                if (due && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                    lastEmittedAtMillis = now
                    trySend(toReading(rotationMatrix, orientation, accuracy, now))
                }
            }

            override fun onAccuracyChanged(changed: Sensor?, status: Int) {
                // Heading error comes from the magnetometer; accelerometer accuracy says nothing
                // about whether the compass needs calibrating.
                if (changed?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    accuracy = SensorAccuracy.fromPlatformStatus(status)
                }
            }
        }

        manager.registerListener(listener, accelerometer, UPDATE_INTERVAL_MICROS)
        manager.registerListener(listener, magnetometer, UPDATE_INTERVAL_MICROS)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    private fun toReading(
        rotationMatrix: FloatArray,
        orientation: FloatArray,
        accuracy: SensorAccuracy,
        takenAtMillis: Long,
    ): CompassReading {
        SensorManager.getOrientation(rotationMatrix, orientation)
        return CompassReading(
            azimuthDegrees = toBearing(orientation[AZIMUTH_INDEX]),
            accuracy = accuracy,
            // Altitude belongs to the position fix, not to orientation - the
            // compass tile combines the two streams itself.
            altitudeMeters = null,
            takenAtMillis = takenAtMillis,
        )
    }

    /** Platform azimuth is radians in -PI..PI; a tile wants degrees in 0..360. */
    private fun toBearing(azimuthRadians: Float): Float {
        val degrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
        return (degrees + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
    }

    private companion object {
        const val ROTATION_MATRIX_SIZE = 9
        const val ORIENTATION_SIZE = 3
        const val VECTOR_SIZE = 3
        const val AZIMUTH_INDEX = 0
        const val FULL_CIRCLE_DEGREES = 360f
        const val MIN_UPDATE_INTERVAL_MS = 1000L
        const val UPDATE_INTERVAL_MICROS = 1_000_000
    }
}
