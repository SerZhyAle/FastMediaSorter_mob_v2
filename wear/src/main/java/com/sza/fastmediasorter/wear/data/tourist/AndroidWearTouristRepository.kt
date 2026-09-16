package com.sza.fastmediasorter.wear.data.tourist

import android.Manifest
import android.annotation.SuppressLint
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
private const val MS_TO_KMH = 3.6f
private const val ROTATION_MATRIX_SIZE = 9
private const val ORIENTATION_ANGLES_SIZE = 3
private const val FULL_ROTATION_DEGREES = 360.0
private const val CARDINAL_OFFSET_DEGREES = 22.5f
private const val CARDINAL_SECTOR_DEGREES = 45f
private const val CARDINAL_SECTORS_COUNT = 8
private const val TEMPERATURE_TOKEN = "temperature"

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
                sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null,
            hasPressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null,
            hasStepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
                sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null,
            hasHeartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null,
            hasBodyTemperatureSensor = resolveTemperatureSensor(sensorManager) != null,
        )

        val updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit = { transform ->
            currentState = transform(currentState)
            trySend(currentState)
        }

        val locationListener = createLocationListener(updateAndEmit)
        val gnssCallback = createGnssCallback(updateAndEmit)
        registerLocationAndGnss(locationManager, locationListener, gnssCallback)

        val sensorListener = createSensorListener(updateAndEmit)
        registerSensors(sensorManager, sensorListener)

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

    private fun createLocationListener(
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val speed = if (location.hasSpeed()) location.speed * MS_TO_KMH else null
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
    }

    private fun createGnssCallback(
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ): GnssStatus.Callback? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return object : GnssStatus.Callback() {
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

    // S3155: every call in this function sits behind the hasLocationPermission() early return on its
    // first line and inside the SecurityException catch below. Lint follows neither a custom
    // permission helper nor a catch around the call, so all five findings here are the same blind
    // spot; the guard they claim is missing is the line that opens the body.
    @SuppressLint("MissingPermission")
    @Suppress("LongParameterList")
    private fun registerLocationAndGnss(
        locationManager: LocationManager?,
        locationListener: LocationListener,
        gnssCallback: GnssStatus.Callback?,
    ) {
        if (!hasLocationPermission() || locationManager == null) return
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

    private fun createSensorListener(
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ): SensorEventListener {
        return object : SensorEventListener {
            private val rotationMatrix = FloatArray(ROTATION_MATRIX_SIZE)
            private val orientationAngles = FloatArray(ORIENTATION_ANGLES_SIZE)

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val degrees = (Math.toDegrees(orientationAngles[0].toDouble()) + FULL_ROTATION_DEGREES) %
                            FULL_ROTATION_DEGREES
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
                        val alt = SensorManager.getAltitude(
                            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                            pressureHpa,
                        ).toDouble()
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
                    else -> handleTemperatureEvent(event, updateAndEmit)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    }

    private fun registerSensors(
        sensorManager: SensorManager?,
        listener: SensorEventListener,
    ) {
        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            } ?: sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
            if (hasHeartRatePermission()) {
                sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
                    sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
            resolveTemperatureSensor(sm)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    /**
     * The platform exposes no body-temperature type, so a watch that measures skin temperature reports
     * it either as the ambient type or under a vendor sensor whose name says so. Both are accepted, and
     * a watch matching neither shows no temperature at all.
     */
    private fun resolveTemperatureSensor(sensorManager: SensorManager?): Sensor? {
        val sm = sensorManager ?: return null
        return sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
            ?: sm.getSensorList(Sensor.TYPE_ALL).firstOrNull { sensor ->
                sensor.stringType.contains(TEMPERATURE_TOKEN, ignoreCase = true) ||
                    sensor.name.contains(TEMPERATURE_TOKEN, ignoreCase = true)
            }
    }

    private fun handleTemperatureEvent(
        event: SensorEvent,
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ) {
        val isTemperature = event.sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE ||
            event.sensor.stringType.contains(TEMPERATURE_TOKEN, ignoreCase = true) ||
            event.sensor.name.contains(TEMPERATURE_TOKEN, ignoreCase = true)
        if (!isTemperature) return
        val celsius = event.values.firstOrNull() ?: return
        updateAndEmit { it.copy(bodyTemperatureCelsius = celsius) }
    }

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
        val index = ((degrees + CARDINAL_OFFSET_DEGREES) / CARDINAL_SECTOR_DEGREES)
            .toInt() % CARDINAL_SECTORS_COUNT
        return directions[index]
    }
}
