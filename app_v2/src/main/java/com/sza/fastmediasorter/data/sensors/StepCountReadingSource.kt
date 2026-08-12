package com.sza.fastmediasorter.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.model.sensors.StepReading
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1179: the system step counter, reported verbatim.
 *
 * Strategic §6 item 2 fixes `TYPE_STEP_COUNTER` as the only source and rules out an accelerometer
 * algorithm of our own, so nothing here derives, smooths or re-bases the number.
 *
 * A device without the sensor gets an empty flow rather than an exception: strategic §3.2 degrades
 * that case to "not offered" in the picker, and a collector that somehow still subscribes must not
 * crash the desktop.
 */
@Singleton
class StepCountReadingSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val availability: SensorAvailabilityRepository,
) {

    fun readings(): Flow<StepReading> {
        val supported = availability.isAvailable(SensorCapability.STEP_COUNTER)
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        // One return over an if/else rather than a guard-return per condition: detekt caps a
        // function at two returns (ReturnCount).
        return if (!supported || manager == null || sensor == null) {
            emptyFlow()
        } else {
            callbackFlow {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val steps = event.values.firstOrNull()?.toLong() ?: return
                        trySend(
                            StepReading(
                                stepsSinceBoot = steps,
                                takenAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }

                    override fun onAccuracyChanged(changed: Sensor?, status: Int) = Unit
                }

                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                awaitClose { manager.unregisterListener(listener) }
            }.conflate()
        }
    }
}
