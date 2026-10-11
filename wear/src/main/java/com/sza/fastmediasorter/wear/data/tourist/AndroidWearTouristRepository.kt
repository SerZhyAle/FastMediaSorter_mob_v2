package com.sza.fastmediasorter.wear.data.tourist

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
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
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.di.ApplicationScope
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import com.sza.fastmediasorter.wear.domain.repository.WearTouristRepository
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearCadenceMeter
import com.sza.fastmediasorter.wear.domain.tourist.WearElevationSource
import com.sza.fastmediasorter.wear.domain.tourist.WearPressureSample
import com.sza.fastmediasorter.wear.domain.tourist.WearSolarCalculator
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristTripTracker
import com.sza.fastmediasorter.wear.domain.tourist.WearTripMotionPolicy
import com.sza.fastmediasorter.wear.domain.tourist.WearTripSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val UPDATE_THROTTLE_MS = 500L
private const val AGE_TICK_INTERVAL_MS = 1_000L
private const val PRESSURE_SAMPLE_INTERVAL_MS = 1_000L
private const val MIN_LOCATION_TIME_MS = 1_000L
private const val MIN_LOCATION_DISTANCE_M = 1.0f
private const val MS_TO_KMH = 3.6f
private const val MILLIS_PER_SECOND = 1_000.0
private const val ROTATION_MATRIX_SIZE = 9
private const val ORIENTATION_ANGLES_SIZE = 3
private const val FULL_ROTATION_DEGREES = 360.0
private const val CARDINAL_OFFSET_DEGREES = 22.5f
private const val CARDINAL_SECTOR_DEGREES = 45f
private const val CARDINAL_SECTORS_COUNT = 8
private const val TEMPERATURE_TOKEN = "temperature"
private const val MIN_SATELLITES_FOR_FIX = 4

/** The last GPS altitude with what the barometer needs to judge it as an anchor. */
private data class GpsAltitudeFix(
    val altitudeMeters: Double,
    val verticalAccuracyMeters: Float?,
    val isEllipsoid: Boolean,
)

/**
 * S3007: Android implementation of [WearTouristRepository] aggregating GPS, GNSS, orientation,
 * pressure and step sensors on Wear OS.
 *
 * S4146: the trip belongs to [WearTouristTripTracker]; this class only feeds it - GPS fixes with a
 * trusted accuracy for distance, the barometer (or GPS) for elevation - and copies its snapshot into
 * the state once a second.
 */
@Singleton
class AndroidWearTouristRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripTracker: WearTouristTripTracker,
    private val environmentSensors: WearTouristEnvironmentSensors,
    @ApplicationScope private val appScope: CoroutineScope,
) : WearTouristRepository {

    private val baseStepCount = AtomicLong(-1L)
    private val sessionSteps = AtomicLong(0L)
    private val cadenceMeter = WearCadenceMeter()

    @Volatile
    private var lastGpsLocation: Location? = null

    @Volatile
    private var lastAltitudeFix: GpsAltitudeFix? = null

    @Volatile
    private var lastPressureAtMillis = 0L

    // The anchor is dropped with the trip: kept, the first fix after a reset would add the leg walked
    // before the reset to the new trip.
    override suspend fun resetTrip() {
        lastGpsLocation = null
        tripTracker.reset()
        Timber.d("S4146: trip reset - distance, time, ascent and descent zeroed")
    }

    override fun resetSteps() {
        baseStepCount.set(-1L)
        sessionSteps.set(0L)
        cadenceMeter.clear()
    }

    @OptIn(FlowPreview::class)
    override fun observeTelemetry(): Flow<WearTouristState> = callbackFlow {
        tripTracker.ensureLoaded()
        Timber.d("S4146: tourist telemetry subscribed, stored trip loaded")
        // A new subscription is a return to the screen: its first fix only anchors, so the walk done
        // while the section was closed is never added as a straight line.
        lastGpsLocation = null
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        var currentState = initialState(sensorManager)

        val updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit = { transform ->
            currentState = transform(currentState)
            trySend(currentState)
        }

        val locationListener = createLocationListener(updateAndEmit)
        val gnssCallback = createGnssCallback(updateAndEmit)
        registerLocationAndGnss(locationManager, locationListener, gnssCallback)

        val sensorListener = createSensorListener(updateAndEmit)
        registerSensors(sensorManager, sensorListener)
        val environmentListener = environmentSensors.register(sensorManager, updateAndEmit)

        trySend(currentState)

        val ticker = launch {
            while (isActive) {
                delay(AGE_TICK_INTERVAL_MS)
                val trip = tripTracker.snapshot.value
                val grade = tripTracker.slopePercent()
                updateAndEmit { prev ->
                    withTrip(prev, trip, grade).copy(
                        hasLocationPermission = hasLocationPermission(),
                        stepCount = sessionSteps.get(),
                    )
                }
                tripTracker.persistIfDue(System.currentTimeMillis())
            }
        }

        awaitClose {
            Timber.d("S4146: tourist telemetry released, sensors unregistered")
            ticker.cancel()
            sensorManager?.unregisterListener(sensorListener)
            environmentSensors.unregister(sensorManager, environmentListener)
            locationManager?.removeUpdates(locationListener)
            locationManager?.unregisterGnssStatusCallback(gnssCallback)
            // The flow's own scope is already cancelled here, so the last write rides the process scope.
            appScope.launch { tripTracker.persist() }
        }
    }.sample(UPDATE_THROTTLE_MS)

    private fun initialState(sensorManager: SensorManager?): WearTouristState {
        val environment = environmentSensors.capabilities(sensorManager)
        return withTrip(WearTouristState(), tripTracker.snapshot.value, tripTracker.slopePercent()).copy(
            focusedMetric = TouristMetricType.SPEED,
            stepCount = sessionSteps.get(),
            hasLocationPermission = hasLocationPermission(),
            // Heading comes from the rotation vector alone (registerSensors), so only that sensor may
            // claim a compass; accelerometer plus magnetometer would show one that never points.
            hasCompassSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null,
            hasPressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null,
            hasStepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
                sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null,
            hasHeartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null,
            hasBodyTemperatureSensor = resolveTemperatureSensor(sensorManager) != null,
            hasLightSensor = environment.hasLight,
            hasMagneticSensor = environment.hasMagnetic,
            hasLevelSensor = environment.hasLevel,
        )
    }

    /** Average speed is distance over moving time, not over clock time: a lunch stop does not slow it. */
    private fun withTrip(state: WearTouristState, trip: WearTripSnapshot, grade: Float?): WearTouristState {
        val movingSeconds = trip.movingMillis / MILLIS_PER_SECOND
        val average = if (movingSeconds > 0.0) (trip.distanceMeters / movingSeconds * MS_TO_KMH).toFloat() else 0f
        return state.copy(
            tripDistanceMeters = trip.distanceMeters,
            maxSpeedKmh = trip.maxSpeedKmh,
            avgSpeedKmh = average,
            tripStartedAtMillis = trip.startedAtMillis,
            movingMillis = trip.movingMillis,
            gainMeters = trip.gainMeters,
            lossMeters = trip.lossMeters,
            elevationSource = trip.elevationSource,
            gradePercent = grade,
        )
    }

    private fun createLocationListener(
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val speed = if (location.hasSpeed()) location.speed * MS_TO_KMH else null
                val accuracy = if (location.hasAccuracy()) location.accuracy else null
                val isGps = location.provider == LocationManager.GPS_PROVIDER
                if (isGps) {
                    onGpsFix(location, speed, accuracy, updateAndEmit)
                }

                val solar = WearSolarCalculator.calculateSunriseSunset(
                    latitude = location.latitude,
                    longitude = location.longitude,
                )

                updateAndEmit { prev ->
                    prev.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speedKmh = speed ?: prev.speedKmh,
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

    /**
     * Only GPS fixes feed the trip: a network fix is a cell or Wi-Fi guess hundreds of metres wide, and
     * mixed into the track it adds zig-zags as distance. A fix outside the trusted accuracy neither adds
     * distance nor becomes the next anchor.
     */
    private fun onGpsFix(
        location: Location,
        speed: Float?,
        accuracy: Float?,
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        val trusted = WearTripMotionPolicy.isTrustedFix(accuracy)
        val previous = lastGpsLocation
        val delta = if (trusted && previous != null) previous.distanceTo(location).toDouble() else 0.0
        tripTracker.onMotion(speed, delta, accuracy, now)
        if (trusted) lastGpsLocation = location

        val altitudeFix = readAltitude(location)
        lastAltitudeFix = altitudeFix ?: lastAltitudeFix
        if (altitudeFix != null && trusted) {
            tripTracker.onElevation(altitudeFix.altitudeMeters, WearElevationSource.GPS)
        }
        val moving = WearTripMotionPolicy.isMoving(speed, accuracy)
        val declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            (altitudeFix?.altitudeMeters ?: 0.0).toFloat(),
            now,
        ).declination
        updateAndEmit { prev ->
            prev.copy(
                altitudeMeters = altitudeFix?.altitudeMeters ?: prev.altitudeMeters,
                gpsAltitudeIsEllipsoid = altitudeFix?.isEllipsoid ?: prev.gpsAltitudeIsEllipsoid,
                gpsAccuracyMeters = accuracy,
                courseDegrees = if (moving && location.hasBearing()) location.bearing else null,
                declinationDegrees = declination,
            )
        }
    }

    /** Height above sea level exists only from API 34; below it the WGS84 ellipsoid height is all there is. */
    private fun readAltitude(location: Location): GpsAltitudeFix? {
        val hasMsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()
        return when {
            hasMsl -> GpsAltitudeFix(
                altitudeMeters = location.mslAltitudeMeters,
                verticalAccuracyMeters = if (location.hasMslAltitudeAccuracy()) {
                    location.mslAltitudeAccuracyMeters
                } else {
                    null
                },
                isEllipsoid = false,
            )
            location.hasAltitude() -> GpsAltitudeFix(
                altitudeMeters = location.altitude,
                verticalAccuracyMeters = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null,
                isEllipsoid = true,
            )
            else -> null
        }
    }

    private fun createGnssCallback(
        updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit,
    ): GnssStatus.Callback {
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
                        hasGpsFix = used >= MIN_SATELLITES_FOR_FIX,
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
        gnssCallback: GnssStatus.Callback,
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

            locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
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
                    Sensor.TYPE_PRESSURE -> onPressure(event.values[0], updateAndEmit)
                    Sensor.TYPE_STEP_COUNTER -> {
                        val rawSteps = event.values[0].toLong()
                        if (baseStepCount.get() < 0) {
                            baseStepCount.set(rawSteps)
                        }
                        val steps = (rawSteps - baseStepCount.get()).coerceAtLeast(0L)
                        sessionSteps.set(steps)
                        onSteps(steps, updateAndEmit)
                    }
                    Sensor.TYPE_STEP_DETECTOR -> onSteps(sessionSteps.incrementAndGet(), updateAndEmit)
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

    private fun onSteps(steps: Long, updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit) {
        val cadence = cadenceMeter.onSteps(steps, System.currentTimeMillis())
        updateAndEmit { it.copy(stepCount = steps, cadenceStepsPerMinute = cadence) }
    }

    /**
     * The barometer reports many times a second; one reading a second is plenty for altitude and
     * trend, and keeps the slope window spanning ground rather than a fraction of a second.
     */
    private fun onPressure(pressureHpa: Float, updateAndEmit: ((WearTouristState) -> WearTouristState) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastPressureAtMillis < PRESSURE_SAMPLE_INTERVAL_MS) return
        lastPressureAtMillis = now
        tripTracker.onPressure(WearPressureSample(now, pressureHpa))
        val fix = lastAltitudeFix
        val baro = tripTracker.baroAltitude(
            pressureHpa = pressureHpa,
            gpsAltitudeM = fix?.altitudeMeters,
            gpsVerticalAccuracyM = fix?.verticalAccuracyMeters,
            gpsAltitudeIsEllipsoid = fix?.isEllipsoid ?: true,
        )
        tripTracker.onElevation(baro.metres, WearElevationSource.BAROMETER)
        val trend = tripTracker.pressureTrend(now)
        updateAndEmit {
            it.copy(
                pressureHpa = pressureHpa,
                pressureTrend = trend,
                baroAltitudeMeters = baro.metres,
                baroAltitudeApproximate = baro.approximate,
            )
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
