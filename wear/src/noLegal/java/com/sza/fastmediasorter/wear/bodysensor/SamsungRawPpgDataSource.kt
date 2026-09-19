package com.sza.fastmediasorter.wear.bodysensor

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.MotionSample
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgCapture
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgSample
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import com.sza.fastmediasorter.wear.domain.bodysensor.PulseWaveLayout
import com.sza.fastmediasorter.wear.domain.bodysensor.WearPpgDataSource
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * S3113: raw pulse-wave windows from Samsung's `com.samsung.sensor.hr_raw` through plain [SensorManager].
 *
 * Measured on the owner's Galaxy Watch 7 (research 02): of every sensor that carries the pulse wave, this
 * is the one an app not signed by Samsung is handed - it is guarded by the heart-rate permission this
 * flavor already declares, while the factory variants and ECG sit behind a signature permission. It is
 * a private vendor type with no public layout, so this class records every channel and decides nothing
 * about which one carries the pulse.
 *
 * What it does decide is the encoding, because that is a fact about this vendor type rather than about
 * signal processing: each value is an integer ADC count stored in the bits of a `float` (research 03 -
 * `1.4E-45` is the count 1). Read as a float every channel looks like zero, so the bits are decoded here
 * and the domain receives counts. Counts stay below 2^24 and are therefore exact as a `Float` again.
 *
 * No `SDK_INT` gate: unlike Health Services, [SensorManager] predates the module's floor, and a watch
 * without the vendor type simply answers [BodySensorUnavailableReason.NO_HARDWARE].
 */
class SamsungRawPpgDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : WearPpgDataSource {

    /**
     * The permission is asked BEFORE the sensor is looked up: `SensorService` leaves a permission-guarded
     * sensor out of the list an app receives until the grant exists, so the lookup alone would call a
     * refused permission "no hardware" and send the user after a sensor the watch does have.
     */
    override suspend fun unavailableReason(): BodySensorUnavailableReason? = when {
        !isHeartRatePermissionGranted() -> BodySensorUnavailableReason.PERMISSION_DENIED
        sensorManager()?.let(::findRawPpgSensor) == null -> BodySensorUnavailableReason.NO_HARDWARE
        else -> null
    }

    override fun capture(durationMillis: Long): Flow<PpgCapture> = flow {
        val reason = unavailableReason()
        val manager = sensorManager()
        val sensor = manager?.let(::findRawPpgSensor)
        when {
            reason != null -> emit(PpgCapture.Unavailable(reason))
            manager == null || sensor == null -> emit(PpgCapture.Unavailable(BodySensorUnavailableReason.NO_HARDWARE))
            else -> emitAll(record(manager, sensor, durationMillis))
        }
    }

    private fun record(
        manager: SensorManager,
        ppgSensor: Sensor,
        durationMillis: Long
    ): Flow<PpgCapture> = callbackFlow {
        val buffer = WindowBuffer(startedAtMillis = System.currentTimeMillis())
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                buffer.add(event)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = manager.registerListener(listener, ppgSensor, SAMPLING_PERIOD_US)
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { accelerometer ->
            manager.registerListener(listener, accelerometer, SAMPLING_PERIOD_US)
        }
        manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)?.let { offBody ->
            manager.registerListener(listener, offBody, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val window = launch {
            val terminal = if (registered) {
                recordWindow(buffer, durationMillis)
            } else {
                Timber.w("The raw pulse-wave sensor refused registration")
                PpgCapture.Unavailable(BodySensorUnavailableReason.MEASUREMENT_FAILED)
            }
            send(terminal)
            close()
        }

        awaitClose {
            window.cancel()
            manager.unregisterListener(listener)
        }
    }

    /**
     * Runs the clock of one window. Two things end it early. SILENCE: an accepted registration that
     * delivers no pulse-wave event within [FIRST_SAMPLE_TIMEOUT_MS] would otherwise leave the screen
     * counting down to an empty window. OFF BODY: the raw sensor keeps delivering with the watch on a desk,
     * and that window is noise the signal-quality rules could only call "weak", sending the user to hold
     * still instead of to put the watch on.
     */
    private suspend fun ProducerScope<PpgCapture>.recordWindow(
        buffer: WindowBuffer,
        durationMillis: Long
    ): PpgCapture {
        val startedAt = SystemClock.elapsedRealtime()
        var elapsed = 0L
        var silent = false
        while (elapsed < durationMillis && !silent && !buffer.isOffBody()) {
            send(PpgCapture.Capturing(elapsedMillis = elapsed, totalMillis = durationMillis))
            delay(PROGRESS_INTERVAL_MS)
            elapsed = SystemClock.elapsedRealtime() - startedAt
            silent = elapsed >= FIRST_SAMPLE_TIMEOUT_MS && buffer.isPpgEmpty()
        }
        return when {
            buffer.isOffBody() -> PpgCapture.Unavailable(BodySensorUnavailableReason.SENSOR_OFF_BODY)
            silent -> PpgCapture.Unavailable(BodySensorUnavailableReason.MEASUREMENT_TIMED_OUT)
            else -> PpgCapture.Captured(buffer.snapshot())
        }
    }

    private fun sensorManager(): SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private fun findRawPpgSensor(manager: SensorManager): Sensor? =
        manager.getSensorList(Sensor.TYPE_ALL).firstOrNull { it.stringType == RAW_PPG_STRING_TYPE }

    private fun isHeartRatePermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        heartRatePermission()
    ) == PackageManager.PERMISSION_GRANTED

    /** Filled on the sensor thread and read by the window clock, hence the concurrent queues. */
    private class WindowBuffer(private val startedAtMillis: Long) {
        private val ppg = ConcurrentLinkedQueue<PpgSample>()
        private val motion = ConcurrentLinkedQueue<MotionSample>()
        private val offBody = AtomicBoolean(false)
        private val startupSampleSkipped = AtomicBoolean(false)

        fun add(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> motion.add(MotionSample(event.timestamp, magnitudeOf(event.values)))
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> offBody.set(event.values[0] == OFF_BODY)
                else -> addPulseEvent(event)
            }
        }

        /**
         * The first event after registration carries the converter's resting value 2^21 on every optical
         * channel instead of a measurement (research 03), and one step of that size would read as a beat.
         */
        private fun addPulseEvent(event: SensorEvent) {
            if (startupSampleSkipped.getAndSet(true)) {
                ppg.add(PpgSample(event.timestamp, event.values.map { it.toRawBits().toFloat() }))
            }
        }

        fun isPpgEmpty(): Boolean = ppg.isEmpty()

        fun isOffBody(): Boolean = offBody.get()

        fun snapshot(): PpgWindow = PpgWindow(
            startedAtMillis = startedAtMillis,
            ppg = ppg.toList(),
            motion = motion.toList(),
            layout = RAW_PPG_LAYOUT
        )

        private fun magnitudeOf(values: FloatArray): Float =
            sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
    }

    private companion object {
        const val RAW_PPG_STRING_TYPE = "com.samsung.sensor.hr_raw"
        const val PULSE_CHANNEL = 5
        val RAW_PPG_LAYOUT = PulseWaveLayout(channel = PULSE_CHANNEL, inverted = true)

        // The sensor's own reported rate is 25 Hz; asking for exactly that keeps the accelerometer on the
        // same grid instead of flooding the window at its 100 Hz maximum.
        const val SAMPLING_PERIOD_US = 40_000
        const val PROGRESS_INTERVAL_MS = 1_000L
        const val FIRST_SAMPLE_TIMEOUT_MS = 5_000L

        // The platform contract of the off-body type: 1.0 on the wrist, 0.0 off it.
        const val OFF_BODY = 0f
    }
}
