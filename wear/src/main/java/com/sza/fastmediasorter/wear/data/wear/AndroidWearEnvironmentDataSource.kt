package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentKind
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentReading
import com.sza.fastmediasorter.wear.domain.repository.WearReadingAccuracy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * How long one burst waits. Long enough for a sensor at `SENSOR_DELAY_UI` to deliver its first event on
 * a cold radio, short enough that a watch whose barometer never answers does not hold the whole report
 * behind a spinner.
 */
private const val SAMPLE_TIMEOUT_MS = 1200L

/** A magnetometer answers on three axes; anything shorter is not a vector and is read as a scalar. */
private const val AXES = 3

private val SENSOR_TYPES = mapOf(
    WearEnvironmentKind.ILLUMINANCE to Sensor.TYPE_LIGHT,
    WearEnvironmentKind.PRESSURE to Sensor.TYPE_PRESSURE,
    WearEnvironmentKind.MAGNETIC_FIELD to Sensor.TYPE_MAGNETIC_FIELD
)

/**
 * Takes one bounded sample from the watch's public environmental sensors.
 *
 * No permission is added by any of this: light, pressure and magnetic field are public sensors, and the
 * body sensors that would need one are excluded on the S2013 ground S2165 recorded.
 *
 * Every reading is mapped to a domain type here rather than handed upwards, because `SensorEvent`
 * cannot be constructed on the plain JVM the unit suite runs on - a contributor holding one could not be
 * tested at all. This is the same split `AndroidWearHardwareDataSource` already makes for descriptors.
 */
class AndroidWearEnvironmentDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : WearEnvironmentDataSource {

    override suspend fun sample(): List<WearEnvironmentReading>? = runCatching { collect() }
        .onFailure { error -> Timber.w(error, "System info: environment readings unavailable") }
        .getOrNull()

    private suspend fun collect(): List<WearEnvironmentReading>? {
        val manager = sensorManager() ?: return null
        val present = SENSOR_TYPES.mapNotNull { (kind, type) ->
            manager.getDefaultSensor(type)?.let { sensor -> kind to sensor }
        }.toMap()
        val measured = if (present.isEmpty()) emptyMap() else awaitFirstEvents(manager, present)
        Timber.d("S2459: %d sensor(s) fitted, %d answered in time", present.size, measured.size)
        return SENSOR_TYPES.keys.map { kind -> readingFor(kind, present, measured) }
    }

    /**
     * The collected map is declared outside the timeout on purpose: a watch where the light sensor
     * answers and the barometer stays silent must still report the first, and a map living inside the
     * cancelled scope would be discarded whole.
     *
     * The listener is torn down in `finally`, which is what makes leaving the screen stop the sampling
     * (S2459 §11 criterion 2): a caller that walked away cancels this coroutine, and the sensor is
     * released on the way out rather than being left powered by a screen nobody is looking at.
     */
    private suspend fun awaitFirstEvents(
        manager: SensorManager,
        present: Map<WearEnvironmentKind, Sensor>
    ): Map<WearEnvironmentKind, WearEnvironmentReading.Measured> {
        val collected = ConcurrentHashMap<WearEnvironmentKind, WearEnvironmentReading.Measured>()
        val gate = AtomicReference<CancellableContinuation<Unit>?>(null)
        val listener = collector(present, collected, gate)
        try {
            withTimeoutOrNull(SAMPLE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    gate.set(continuation)
                    present.values.forEach { sensor ->
                        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                    }
                }
            }
        } finally {
            manager.unregisterListener(listener)
        }
        return collected
    }

    private fun readingFor(
        kind: WearEnvironmentKind,
        present: Map<WearEnvironmentKind, Sensor>,
        measured: Map<WearEnvironmentKind, WearEnvironmentReading.Measured>
    ): WearEnvironmentReading = if (present.containsKey(kind)) {
        measured[kind] ?: WearEnvironmentReading.Silent(kind)
    } else {
        WearEnvironmentReading.Unsupported(kind)
    }

    private fun sensorManager(): SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
}

/**
 * One listener for all three sensors, resuming the caller as soon as each has spoken once.
 *
 * `getAndSet` rather than a plain read: two sensors can deliver on different threads within the same
 * microsecond, and resuming one continuation twice is a crash rather than a duplicated reading.
 */
private fun collector(
    present: Map<WearEnvironmentKind, Sensor>,
    collected: MutableMap<WearEnvironmentKind, WearEnvironmentReading.Measured>,
    gate: AtomicReference<CancellableContinuation<Unit>?>
): SensorEventListener {
    val byType = present.entries.associate { (kind, sensor) -> sensor.type to kind }
    return object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            val kind = byType[event.sensor?.type] ?: return
            collected[kind] = WearEnvironmentReading.Measured(
                kind = kind,
                value = valueOf(kind, event.values),
                accuracy = accuracyOf(event.accuracy)
            )
            if (collected.size >= present.size) {
                gate.getAndSet(null)?.takeIf { waiting -> waiting.isActive }?.resume(Unit)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}

private fun valueOf(kind: WearEnvironmentKind, values: FloatArray?): Float =
    if (kind == WearEnvironmentKind.MAGNETIC_FIELD) {
        magnitude(values)
    } else {
        values?.firstOrNull() ?: 0f
    }

/** The compass reading the user can check is the field strength, not the three axes it is built from. */
private fun magnitude(values: FloatArray?): Float {
    if (values == null || values.size < AXES) {
        return values?.firstOrNull() ?: 0f
    }
    return sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
}

/**
 * Null for anything the platform does not name, including `SENSOR_STATUS_NO_CONTACT`: an unrated sample
 * is shown as the plain measurement, and only an explicit `UNRELIABLE` replaces the number with a word.
 */
private fun accuracyOf(accuracy: Int): WearReadingAccuracy? = when (accuracy) {
    SensorManager.SENSOR_STATUS_UNRELIABLE -> WearReadingAccuracy.UNRELIABLE
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> WearReadingAccuracy.LOW
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> WearReadingAccuracy.MEDIUM
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> WearReadingAccuracy.HIGH
    else -> null
}
