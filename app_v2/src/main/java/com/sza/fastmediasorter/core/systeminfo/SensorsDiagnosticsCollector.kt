package com.sza.fastmediasorter.core.systeminfo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Collects zero-permission physical sensor inventory and a bounded snapshot of environmental
 * sensors (ambient light, atmospheric pressure, magnetic field) for the System info `Sensors`
 * section. Permission-gated health and activity-recognition sensors (`TYPE_STEP_COUNTER`,
 * `TYPE_STEP_DETECTOR`, `TYPE_HEART_RATE`, etc.) are isolated in `NoLegalDiagnosticsCollectors`.
 */
class SensorsDiagnosticsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun collectSection(): SystemInfoSection {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val allSensors = runCatching { sm?.getSensorList(Sensor.TYPE_ALL).orEmpty() }
            .onFailure { Timber.w(it, "System info: failed to list sensors") }
            .getOrDefault(emptyList())
            .filterNot { it.type in PERMISSION_GATED_SENSOR_TYPES }

        val fields = mutableListOf<Pair<String, String>>()
        fields += label(R.string.sysinfo_field_sensors_count) to allSensors.size.toString()
        fields += label(R.string.sysinfo_field_sensors_key) to keySensorsSummary(sm)
        fields += label(R.string.sysinfo_field_env_light) to
            sampleEnvironmentSensor(sm, Sensor.TYPE_LIGHT) { String.format(Locale.US, "%.0f lx", it) }
        fields += label(R.string.sysinfo_field_env_pressure) to
            sampleEnvironmentSensor(sm, Sensor.TYPE_PRESSURE) { String.format(Locale.US, "%.1f hPa", it) }
        fields += label(R.string.sysinfo_field_env_magnetic) to
            sampleEnvironmentSensorArray(sm, Sensor.TYPE_MAGNETIC_FIELD) { values ->
                val magnitude = kotlin.math.sqrt(
                    (values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()
                )
                String.format(Locale.US, "%.1f uT", magnitude)
            }
        if (allSensors.isNotEmpty()) {
            fields += label(R.string.sysinfo_field_sensors_list) to formatSensorList(allSensors)
        }
        return SystemInfoSection(
            title = label(R.string.sysinfo_section_sensors),
            fields = fields,
            group = SystemInfoGroup.SENSORS,
        )
    }

    private fun keySensorsSummary(sm: SensorManager?): String {
        if (sm == null) return UNKNOWN
        val checks = listOf(
            "Accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "Gyroscope" to Sensor.TYPE_GYROSCOPE,
            "Magnetometer" to Sensor.TYPE_MAGNETIC_FIELD,
            "Barometer" to Sensor.TYPE_PRESSURE,
            "Light" to Sensor.TYPE_LIGHT,
            "Proximity" to Sensor.TYPE_PROXIMITY,
            "Gravity" to Sensor.TYPE_GRAVITY,
            "Rotation vector" to Sensor.TYPE_ROTATION_VECTOR,
            "Hinge angle" to SENSOR_TYPE_HINGE_ANGLE,
        )
        val present = checks.mapNotNull { (name, type) ->
            name.takeIf { runCatching { sm.getDefaultSensor(type) != null }.getOrDefault(false) }
        }
        return if (present.isEmpty()) "none" else present.joinToString(", ")
    }

    private fun sampleEnvironmentSensor(
        sm: SensorManager?,
        sensorType: Int,
        formatter: (Float) -> String,
    ): String = sampleEnvironmentSensorArray(sm, sensorType) { values ->
        formatter(values.firstOrNull() ?: 0f)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sampleEnvironmentSensorArray(
        sm: SensorManager?,
        sensorType: Int,
        formatter: (FloatArray) -> String,
    ): String {
        val sensor = runCatching { sm?.getDefaultSensor(sensorType) }.getOrNull() ?: return "unsupported"
        var captured: FloatArray? = null
        val latch = CountDownLatch(1)
        val thread = HandlerThread("sysinfo-sensor-$sensorType").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values
                if (values != null && values.isNotEmpty()) {
                    captured = values.clone()
                    latch.countDown()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        return try {
            val registered = sm?.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                Handler(thread.looper),
            ) == true
            if (registered) {
                latch.await(SAMPLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            captured?.let(formatter) ?: "supported (${sensor.name})"
        } catch (e: Exception) {
            Timber.w(e, "System info: sensor sample failed for type %d", sensorType)
            "supported (${sensor.name})"
        } finally {
            runCatching { sm?.unregisterListener(listener) }
            runCatching { thread.quitSafely() }
        }
    }

    private fun formatSensorList(sensors: List<Sensor>): String {
        val shown = sensors.take(MAX_LISTED_SENSORS).map { sensor ->
            String.format(Locale.US, "%s (%s, %.2f mA)", sensor.name, sensor.vendor, sensor.power)
        }
        val suffix = if (sensors.size > MAX_LISTED_SENSORS) {
            ", .. (+${sensors.size - MAX_LISTED_SENSORS})"
        } else {
            ""
        }
        return shown.joinToString("; ") + suffix
    }

    private fun label(@StringRes resId: Int): String = context.getString(resId)

    private companion object {
        const val UNKNOWN = "unknown"
        const val SAMPLE_TIMEOUT_MS = 80L
        const val MAX_LISTED_SENSORS = 15
        const val SENSOR_TYPE_HINGE_ANGLE = 36
        const val SENSOR_TYPE_HEART_BEAT = 31
        const val SENSOR_TYPE_LOW_LATENCY_OFFBODY_DETECT = 34
        val PERMISSION_GATED_SENSOR_TYPES = setOf(
            Sensor.TYPE_STEP_COUNTER,
            Sensor.TYPE_STEP_DETECTOR,
            Sensor.TYPE_HEART_RATE,
            SENSOR_TYPE_HEART_BEAT,
            SENSOR_TYPE_LOW_LATENCY_OFFBODY_DETECT,
        )
    }
}
