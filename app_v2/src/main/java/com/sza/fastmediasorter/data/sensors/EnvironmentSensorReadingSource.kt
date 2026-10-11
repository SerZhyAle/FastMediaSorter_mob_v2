package com.sza.fastmediasorter.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sza.fastmediasorter.domain.model.sensors.LevelReading
import com.sza.fastmediasorter.domain.model.sensors.LightReading
import com.sza.fastmediasorter.domain.model.sensors.MagneticFieldReading
import com.sza.fastmediasorter.domain.model.sensors.PressureReading
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * S4145: barometer, light, magnetic field and tilt for the Tourist dashboard.
 *
 * Cold by construction, like [OrientationReadingSource]: each listener is registered when a collector
 * arrives and unregistered in `awaitClose`, so nothing stays subscribed once the dashboard stops
 * collecting (strategic ADR-1).
 */
@Singleton
class EnvironmentSensorReadingSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun pressure(): Flow<PressureReading> = sensorFlow(Sensor.TYPE_PRESSURE) { event, _, now ->
        PressureReading(hectopascals = event.values[0], takenAtMillis = now)
    }

    fun light(): Flow<LightReading> = sensorFlow(Sensor.TYPE_LIGHT) { event, _, now ->
        LightReading(lux = event.values[0], takenAtMillis = now)
    }

    fun magneticField(): Flow<MagneticFieldReading> =
        sensorFlow(Sensor.TYPE_MAGNETIC_FIELD) { event, accuracy, now ->
            MagneticFieldReading(
                microtesla = magnitude(event.values),
                accuracy = accuracy,
                takenAtMillis = now,
            )
        }

    /** Gravity is the low-pass vector; the raw accelerometer is the fallback and is right at rest. */
    fun level(): Flow<LevelReading> {
        val type = if (defaultSensor(Sensor.TYPE_GRAVITY) != null) {
            Sensor.TYPE_GRAVITY
        } else {
            Sensor.TYPE_ACCELEROMETER
        }
        return sensorFlow(type) { event, _, now ->
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            LevelReading(
                pitchDegrees = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat(),
                rollDegrees = Math.toDegrees(atan2(-x.toDouble(), z.toDouble())).toFloat(),
                takenAtMillis = now,
            )
        }
    }

    private fun defaultSensor(type: Int): Sensor? =
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(type)

    private fun <T> sensorFlow(
        type: Int,
        mapper: (SensorEvent, SensorAccuracy, Long) -> T,
    ): Flow<T> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(type)
        return if (manager == null || sensor == null) {
            emptyFlow()
        } else {
            callbackFlow {
                var accuracy = SensorAccuracy.UNRELIABLE
                var lastEmittedAtMillis = 0L
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val now = System.currentTimeMillis()
                        if (now - lastEmittedAtMillis < MIN_UPDATE_INTERVAL_MS) return
                        lastEmittedAtMillis = now
                        trySend(mapper(event, accuracy, now))
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

    private fun magnitude(values: FloatArray): Float =
        sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])

    private companion object {
        const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
