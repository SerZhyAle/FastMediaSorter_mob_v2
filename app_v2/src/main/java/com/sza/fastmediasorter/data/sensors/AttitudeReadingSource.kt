package com.sza.fastmediasorter.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sza.fastmediasorter.domain.model.sensors.AttitudeReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * S4560: device attitude for the Tourist gyroscope tile, at game rate.
 *
 * The game rotation vector is preferred because it ignores the magnetometer, so a nearby magnet cannot
 * make the box jump; the geomagnetic rotation vector is the fallback. The gyroscope only supplies the
 * turning speed. Cold like the other sources: one listener for both sensors, registered when a collector
 * arrives and dropped in `awaitClose`, so a single unregister releases the pair.
 *
 * `SENSOR_DELAY_GAME` is about 50 Hz, under the 200 Hz line above which `HIGH_SAMPLING_RATE_SENSORS`
 * would have to be declared.
 */
@Singleton
class AttitudeReadingSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun readings(): Flow<AttitudeReading> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotation = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        return if (manager == null || rotation == null) {
            emptyFlow()
        } else {
            attitudeFlow(manager, rotation, manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE))
        }
    }

    private fun attitudeFlow(
        manager: SensorManager,
        rotation: Sensor,
        gyroscope: Sensor?,
    ): Flow<AttitudeReading> = callbackFlow {
        var turnRate: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    turnRate = Math.toDegrees(magnitude(event.values).toDouble()).toFloat()
                } else {
                    trySend(toReading(event.values, turnRate))
                }
            }

            override fun onAccuracyChanged(changed: Sensor?, status: Int) = Unit
        }
        manager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        if (gyroscope != null) manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
        Timber.d("S4560: attitude sensors registered gyroscope=${gyroscope != null} rotation=${rotation.type}")
        awaitClose {
            Timber.d("S4560: attitude sensors unregistered")
            manager.unregisterListener(listener)
        }
    }.conflate()

    /**
     * Some drivers omit the fourth component; the vector part of a unit
     * quaternion fixes its magnitude, and the sign of w is free because q and -q are the same rotation.
     */
    private fun toReading(values: FloatArray, turnRate: Float?): AttitudeReading {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val w = if (values.size > W_INDEX) {
            values[W_INDEX]
        } else {
            sqrt((1f - x * x - y * y - z * z).coerceAtLeast(0f))
        }
        return AttitudeReading(x, y, z, w, turnRate)
    }

    private fun magnitude(values: FloatArray): Float =
        sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])

    private companion object {
        const val W_INDEX = 3
    }
}
