package com.sza.fastmediasorter.wear.data.tourist

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sza.fastmediasorter.wear.domain.tourist.WearTiltCalculator
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * S4146: the light, magnetic-field and level feeds of the watch Tourist section.
 *
 * Split from [AndroidWearTouristRepository] so its location and step wiring stays readable. The
 * listener lives exactly as long as the repository's telemetry flow: registered when the section is
 * collected, unregistered in the same `awaitClose`.
 */
class WearTouristEnvironmentSensors @Inject constructor() {

    data class Capabilities(
        val hasLight: Boolean,
        val hasMagnetic: Boolean,
        val hasLevel: Boolean,
    )

    fun capabilities(sensorManager: SensorManager?): Capabilities = Capabilities(
        hasLight = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) != null,
        hasMagnetic = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null,
        hasLevel = levelSensor(sensorManager) != null,
    )

    fun register(
        sensorManager: SensorManager?,
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ): SensorEventListener? {
        val sm = sensorManager ?: return null
        val listener = createListener(updateAndEmit)
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        levelSensor(sm)?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        return listener
    }

    fun unregister(sensorManager: SensorManager?, listener: SensorEventListener?) {
        if (listener != null) sensorManager?.unregisterListener(listener)
    }

    private fun levelSensor(sensorManager: SensorManager?): Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private fun createListener(
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ): SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_LIGHT -> {
                    val lux = event.values[0]
                    updateAndEmit { it.copy(lightLux = lux) }
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                    val microtesla = sqrt(x * x + y * y + z * z)
                    updateAndEmit { prev ->
                        prev.copy(
                            magneticMicrotesla = microtesla,
                            magneticInterference = isInterference(microtesla, prev),
                        )
                    }
                }
                Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                    val tilt = WearTiltCalculator.tilt(event.values[0], event.values[1], event.values[2])
                    updateAndEmit { it.copy(pitchDegrees = tilt.pitchDegrees, rollDegrees = tilt.rollDegrees) }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /**
     * Earth's field is roughly 25-65 µT everywhere; a reading outside that band, or a compass the
     * platform itself distrusts, means something nearby is bending the needle. The compass rating counts
     * only once a heading has arrived - before that its zero is "not rated yet", not "unreliable".
     */
    private fun isInterference(microtesla: Float, state: WearTouristState): Boolean {
        val outOfBand = microtesla !in EARTH_FIELD_MIN_UT..EARTH_FIELD_MAX_UT
        val distrusted = state.azimuthDegrees != null &&
            state.compassAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        return outOfBand || distrusted
    }

    private companion object {
        const val EARTH_FIELD_MIN_UT = 25f
        const val EARTH_FIELD_MAX_UT = 65f
    }
}
