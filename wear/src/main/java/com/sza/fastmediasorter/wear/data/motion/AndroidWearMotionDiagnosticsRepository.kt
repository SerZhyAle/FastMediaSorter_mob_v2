package com.sza.fastmediasorter.wear.data.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sza.fastmediasorter.wear.domain.motion.WearSensorAvailability
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamId
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamState
import com.sza.fastmediasorter.wear.domain.motion.accumulate
import com.sza.fastmediasorter.wear.domain.repository.WearMotionDiagnosticsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the watch's motion and activity sensors for exactly as long as somebody collects, and not one
 * event more.
 *
 * The shape is taken verbatim from `WearNetworkMonitorRepositoryImpl`: a `callbackFlow` that registers
 * platform callbacks on collection and tears them down in `awaitClose`. S2458 §7 names an always-on
 * listener as the battery risk of this ticket and fixes that teardown as its mitigation.
 *
 * The periodic tick is not a poll of the sensors - they push. It re-emits the state the listener already
 * holds so the last-event age keeps advancing on screen, which is the only way a stream that accepted
 * registration and then went silent is distinguishable from one delivering normally.
 *
 * The flow is sampled at [UPDATE_THROTTLE_MS] rather than conflated: a diagnostic glance needs the
 * readings no faster than twice a second, and capping the emission rate there spares the battery and
 * the recomposer the per-event churn that `SENSOR_DELAY_UI` would otherwise drive straight to the screen.
 * Accumulation still runs on every sensor event in the listener, so the event count and hertz stay exact.
 *
 * A step stream's availability is asked BEFORE its sensor is touched: registering against a permission
 * that was refused throws instead of producing the explanation S2458 §5.4 requires.
 */
@Singleton
class AndroidWearMotionDiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityRecognitionState: WearActivityRecognitionState
) : WearMotionDiagnosticsRepository {

    @OptIn(FlowPreview::class)
    override fun streams(): Flow<List<WearSensorStreamState>> = callbackFlow {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sessionStartMillis = System.currentTimeMillis()
        val states = ConcurrentHashMap<WearSensorStreamId, WearSensorStreamState>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val id = SENSOR_TYPE_TO_STREAM[event.sensor.type]
                val current = id?.let { states[it] }
                if (id != null && current != null) {
                    states[id] = current.copy(
                        values = readingOf(id, event),
                        delivery = accumulate(
                            previous = current.delivery,
                            eventAtMillis = System.currentTimeMillis(),
                            sessionStartMillis = sessionStartMillis
                        )
                    )
                    trySend(snapshot(states))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        SENSOR_TYPES.forEach { (id, sensorType) ->
            val availability = availabilityOf(id, manager, sensorType)
            states[id] = WearSensorStreamState(id = id, availability = availability)
            if (availability == WearSensorAvailability.Available) {
                manager?.getDefaultSensor(sensorType)?.let { sensor ->
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }

        trySend(snapshot(states))

        val ticker = launch {
            while (isActive) {
                delay(AGE_TICK_INTERVAL_MS)
                trySend(snapshot(states))
            }
        }

        awaitClose {
            ticker.cancel()
            manager?.unregisterListener(listener)
        }
    }.sample(UPDATE_THROTTLE_MS).onEach {
    }

    private fun availabilityOf(
        id: WearSensorStreamId,
        manager: SensorManager?,
        sensorType: Int
    ): WearSensorAvailability = if (id in ACTIVITY_STREAMS) {
        activityRecognitionState.resolve(id)
    } else if (manager?.getDefaultSensor(sensorType) == null) {
        WearSensorAvailability.NoHardware
    } else {
        WearSensorAvailability.Available
    }

    /**
     * The step detector's value is a constant 1 per step, so it carries no information the event count
     * does not already carry; showing it would invite reading a reading where there is none.
     */
    private fun readingOf(id: WearSensorStreamId, event: SensorEvent): List<Float> =
        if (id == WearSensorStreamId.STEP_DETECTOR) {
            emptyList()
        } else {
            event.values.toList()
        }

    private fun snapshot(
        states: Map<WearSensorStreamId, WearSensorStreamState>
    ): List<WearSensorStreamState> = WearSensorStreamId.entries.mapNotNull { states[it] }

    private companion object {
        const val AGE_TICK_INTERVAL_MS = 1000L

        const val UPDATE_THROTTLE_MS = 500L

        val ACTIVITY_STREAMS = setOf(WearSensorStreamId.STEP_COUNTER, WearSensorStreamId.STEP_DETECTOR)

        val SENSOR_TYPES = linkedMapOf(
            WearSensorStreamId.ACCELEROMETER to Sensor.TYPE_ACCELEROMETER,
            WearSensorStreamId.GYROSCOPE to Sensor.TYPE_GYROSCOPE,
            WearSensorStreamId.ROTATION_VECTOR to Sensor.TYPE_ROTATION_VECTOR,
            WearSensorStreamId.STEP_COUNTER to Sensor.TYPE_STEP_COUNTER,
            WearSensorStreamId.STEP_DETECTOR to Sensor.TYPE_STEP_DETECTOR
        )

        val SENSOR_TYPE_TO_STREAM = SENSOR_TYPES.entries.associate { (id, type) -> type to id }
    }
}
