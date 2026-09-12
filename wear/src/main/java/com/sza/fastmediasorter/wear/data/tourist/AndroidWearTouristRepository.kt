package com.sza.fastmediasorter.wear.data.tourist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import com.sza.fastmediasorter.wear.domain.repository.WearTouristRepository
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearSolarCalculator
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val UPDATE_THROTTLE_MS = 500L
private const val AGE_TICK_INTERVAL_MS = 1_000L
private const val MIN_LOCATION_TIME_MS = 1_000L
private const val MIN_LOCATION_DISTANCE_M = 1.0f

/**
 * S3007: Android implementation of [WearTouristRepository] aggregating GPS, GNSS, orientation,
 * pressure and step sensors on Wear OS.
 */
@Singleton
class AndroidWearTouristRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WearTouristRepository {

    private val accumulatedTripMeters = AtomicReference(0.0)
    private val maxSpeedKmh = AtomicReference(0f)
    private val baseStepCount = AtomicLong(-1L)
    private val sessionSteps = AtomicLong(0L)
    private var lastLocation: Location? = null

    override fun resetTrip() {
        accumulatedTripMeters.set(0.0)
        maxSpeedKmh.set(0f)
    }

    override fun resetSteps() {
        baseStepCount.set(-1L)
        sessionSteps.set(0L)
    }

    @OptIn(FlowPreview::class)
    override fun observeTelemetry(): Flow<WearTouristState> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        var currentState = WearTouristState(
            focusedMetric = TouristMetricType.SPEED,
            tripDistanceMeters = accumulatedTripMeters.get(),
            maxSpeedKmh = maxSpeedKmh.get(),
            stepCount = sessionSteps.get(),
            hasLocationPermission = hasLocationPermission(),
            hasCompassSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
                (sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                    sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null),
            hasPressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null,
            hasStepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
                sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null,
            hasHeartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null,
        )

        fun updateAndEmit(transform: (WearTouristState) -> WearTouristState) {
            currentState = transform(currentState)
            trySend(currentState)
        }

        // 1. Location and GNSS Listeners
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val speed = if (location.hasSpeed()) location.speed * 3.6f else null
                if (speed != null && speed > maxSpeedKmh.get()) {
                    maxSpeedKmh.set(speed)
                }

                lastLocation?.let { prev ->
                    val dist = prev.distanceTo(location).toDouble()
                    if (dist > 0.0) {
                        val newDist = accumulatedTripMeters.updateAndGet { it + dist }
                        updateAndEmit { it.copy(tripDistanceMeters = newDist) }
                    }
                }
                lastLocation = location

                val solar = WearSolarCalculator.calculateSunriseSunset(
                    latitude = location.latitude,
                    longitude = location.longitude,
                )

                updateAndEmit { prev ->
                    prev.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speedKmh = speed ?: prev.speedKmh,
                        maxSpeedKmh = maxSpeedKmh.get(),
                        altitudeMeters = if (location.hasAltitude()) location.altitude else prev.altitudeMeters,
                        sunriseMillis = solar.sunriseMillis,
                        sunsetMillis = solar.sunsetMillis,
                        isDaylight = solar.isDaylight,
                        hasGpsFix = true,
                    )
                }
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        var gnssCallback: GnssStatus.Callback? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && locationManager != null) {
            gnssCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val total = status.satelliteCount
                    var used = 0
                    for (i in 0 until total) {
                        if (status.usedInFix(i)) used++
                    }
                    updateAndEmit {
                        it.copy(
                            satelliteCount = total,
                            usedSatellites = used,
                            hasGpsFix = used >= 4,
                        )
                    }
                }
            }
        }

        if (hasLocationPermission() && locationManager != null) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_LOCATION_TIME_MS,
                        MIN_LOCATION_DISTANCE_M,
                        locationListener,
                    )
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_LOCATION_TIME_MS,
                        MIN_LOCATION_DISTANCE_M,
                        locationListener,
                    )
                }
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                lastKnown?.let { locationListener.onLocationChanged(it) }

                gnssCallback?.let {
                    locationManager.registerGnssStatusCallback(context.mainExecutor, it)
                }
            } catch (e: SecurityException) {
                Timber.w(e, "Location permission not granted or revoked during registration")
            }
        }

        // 2. Sensor Listeners (Compass, Barometer, Steps)
        val sensorListener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val degrees = (Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0
                        val cardinal = degreeToCardinal(degrees.toFloat())
                        updateAndEmit {
                            it.copy(
                                azimuthDegrees = degrees.toFloat(),
                                cardinalDirection = cardinal,
                                compassAccuracy = event.accuracy,
                            )
                        }
                    }
                    Sensor.TYPE_PRESSURE -> {
                        val pressureHpa = event.values[0]
                        val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa).toDouble()
                        updateAndEmit { it.copy(altitudeMeters = alt) }
                    }
                    Sensor.TYPE_STEP_COUNTER -> {
                        val rawSteps = event.values[0].toLong()
                        if (baseStepCount.get() < 0) {
                            baseStepCount.set(rawSteps)
                        }
                        val steps = (rawSteps - baseStepCount.get()).coerceAtLeast(0L)
                        sessionSteps.set(steps)
                        updateAndEmit { it.copy(stepCount = steps) }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        val steps = sessionSteps.incrementAndGet()
                        updateAndEmit { it.copy(stepCount = steps) }
                    }
                    Sensor.TYPE_HEART_RATE -> {
                        val bpm = event.values[0].roundToInt()
                        if (bpm > 0) {
                            updateAndEmit { it.copy(heartRateBpm = bpm) }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
            } ?: sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
            }
            if (hasHeartRatePermission()) {
                sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
                    sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }

        trySend(currentState)

        val ticker = launch {
            while (isActive) {
                delay(AGE_TICK_INTERVAL_MS)
                updateAndEmit { prev ->
                    prev.copy(
                        hasLocationPermission = hasLocationPermission(),
                        tripDistanceMeters = accumulatedTripMeters.get(),
                        maxSpeedKmh = maxSpeedKmh.get(),
                        stepCount = sessionSteps.get(),
                    )
                }
            }
        }

        awaitClose {
            ticker.cancel()
            sensorManager?.unregisterListener(sensorListener)
            locationManager?.removeUpdates(locationListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                locationManager?.unregisterGnssStatusCallback(gnssCallback)
            }
        }
    }.sample(UPDATE_THROTTLE_MS)

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasHeartRatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            heartRatePermission(),
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun degreeToCardinal(degrees: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((degrees + 22.5f) / 45f).toInt() % 8
        return directions[index]
    }
}

