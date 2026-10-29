package com.sza.fastmediasorter.domain.usecase.tourist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.data.networkmonitor.GnssStatusDataSource
import com.sza.fastmediasorter.data.sensors.MotionReadingSource
import com.sza.fastmediasorter.data.sensors.OrientationReadingSource
import com.sza.fastmediasorter.data.sensors.StepCountReadingSource
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.sensors.MotionReading
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.model.sensors.StepReading
import com.sza.fastmediasorter.domain.model.tourist.ElevationSource
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.model.tourist.TouristTripSnapshot
import com.sza.fastmediasorter.domain.model.tourist.TouristWeatherDetails
import com.sza.fastmediasorter.domain.model.tourist.TripMotionPolicy
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import com.sza.fastmediasorter.domain.repository.WeatherResult
import com.sza.fastmediasorter.domain.tourist.TouristEnvironmentTelemetry
import com.sza.fastmediasorter.domain.tourist.TouristEnvironmentTelemetry.GpsAltitudeFix
import com.sza.fastmediasorter.domain.tourist.TouristTripTracker
import com.sza.fastmediasorter.domain.util.SolarCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2922/S3000/S3011: combines sensor and GNSS feeds into a unified real-time [TouristDashboardState].
 *
 * S4145: the trip lives in [TouristTripTracker] and is persisted, so it survives leaving the screen and a
 * process restart; every subscription below exists only while the returned flow is collected.
 */
@Singleton
class ObserveTouristDashboardUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val motionReadingSource: MotionReadingSource,
    private val orientationReadingSource: OrientationReadingSource,
    private val stepCountReadingSource: StepCountReadingSource,
    private val gnssStatusDataSource: GnssStatusDataSource,
    private val sensorAvailabilityRepository: SensorAvailabilityRepository,
    private val weatherRepository: WeatherRepository,
    private val tripTracker: TouristTripTracker,
    private val environmentTelemetry: TouristEnvironmentTelemetry,
) {

    @Volatile
    private var baseStepCount: Long? = null

    suspend fun resetTrip() {
        tripTracker.reset()
    }

    fun resetSteps() {
        baseStepCount = null
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED

    @OptIn(FlowPreview::class)
    operator fun invoke(
        initialFocus: TouristTileType = TouristTileType.SPEED,
    ): Flow<TouristDashboardState> = channelFlow {
        tripTracker.ensureLoaded()
        val capabilities = SensorCapability.entries.filter { sensorAvailabilityRepository.isAvailable(it) }.toSet()
        val isStepsAvail = SensorCapability.STEP_COUNTER in capabilities

        val currentState = mutableSlot(
            TouristDashboardState(
                focusedTile = initialFocus,
                hasLocationPermission = hasLocationPermission(),
                hasActivityRecognitionPermission = hasActivityRecognitionPermission(),
                stepsAvailable = isStepsAvail,
                availableCapabilities = capabilities,
            ),
        )
        val seaLevelHpa = mutableSlot<Double?>(null)
        val gpsFix = mutableSlot<GpsAltitudeFix?>(null)

        launch {
            currentState
                .sample(UPDATE_THROTTLE_MS)
                .collectLatest { state ->
                    send(state)
                }
        }

        observeTrip(currentState)
        observeMotionTelemetry(currentState, gpsFix)
        observeCompassTelemetry(currentState)
        observeStepTelemetry(currentState, isStepsAvail)
        observeGnssTelemetry(currentState)
        observeWeatherTelemetry(currentState, seaLevelHpa)
        environmentTelemetry.launchIn(this, currentState, seaLevelHpa, gpsFix)

        try {
            awaitCancellation()
        } finally {
            // The last samples before the screen left would otherwise wait for the next 15 s tick that
            // never comes, and a process death right after would lose them.
            withContext(NonCancellable) { tripTracker.persist() }
        }
    }

    /** The trip snapshot plus a one-second tick, because elapsed time moves while nothing else does. */
    private fun CoroutineScope.observeTrip(currentState: MutableStateFlow<TouristDashboardState>) {
        launch {
            tripTracker.snapshot.collectLatest { trip ->
                while (isActive) {
                    currentState.update { applyTrip(it, trip, System.currentTimeMillis()) }
                    delay(CLOCK_TICK_MS)
                }
            }
        }
    }

    private fun applyTrip(
        prev: TouristDashboardState,
        trip: TouristTripSnapshot,
        nowMillis: Long,
    ): TouristDashboardState {
        val movingHours = trip.movingMillis.toDouble() / TimeUnit.HOURS.toMillis(1)
        val averageKmh = if (movingHours > 0.0) {
            (UnitScale.metresToKilometres(trip.distanceMeters) / movingHours).toFloat()
        } else {
            null
        }
        return prev.copy(
            tripDistanceMeters = trip.distanceMeters,
            maxSpeedKmh = trip.maxSpeedKmh,
            trip = prev.trip.copy(
                elapsedMillis = trip.startedAtMillis?.let { (nowMillis - it).coerceAtLeast(0L) },
                movingMillis = trip.movingMillis,
                gainMeters = trip.gainMeters,
                lossMeters = trip.lossMeters,
                elevationSource = trip.elevationSource,
                averageSpeedKmh = averageKmh,
                slopePercent = tripTracker.slopePercent(),
            ),
        )
    }

    private fun CoroutineScope.observeMotionTelemetry(
        currentState: MutableStateFlow<TouristDashboardState>,
        gpsFix: MutableStateFlow<GpsAltitudeFix?>,
    ) {
        launch {
            motionReadingSource.readings().collect { motion ->
                tripTracker.onMotion(
                    speedKmh = motion.speedKmh,
                    distanceDeltaMeters = motion.distanceDeltaMeters,
                    horizontalAccuracyM = motion.horizontalAccuracyMeters,
                    atMillis = motion.takenAtMillis,
                )
                recordGpsElevation(motion, gpsFix)
                currentState.update { prev -> applyMotion(prev, motion) }
                tripTracker.persistIfDue(motion.takenAtMillis)
            }
        }
    }

    /** GPS altitude counts towards gain and loss only with a tight vertical accuracy - criterion 5. */
    private fun recordGpsElevation(motion: MotionReading, gpsFix: MutableStateFlow<GpsAltitudeFix?>) {
        val altitude = motion.altitudeMeters ?: return
        gpsFix.value = GpsAltitudeFix(altitude, motion.verticalAccuracyMeters)
        val vertical = motion.verticalAccuracyMeters
        if (vertical != null && vertical <= TripMotionPolicy.TRUSTED_VERTICAL_ACCURACY_M) {
            tripTracker.onElevation(altitude, ElevationSource.GPS)
        }
    }

    private fun applyMotion(prev: TouristDashboardState, motion: MotionReading): TouristDashboardState {
        val moving = TripMotionPolicy.isMoving(motion.speedKmh, motion.horizontalAccuracyMeters)
        return prev.copy(
            speedKmh = motion.speedKmh,
            altitudeMeters = motion.altitudeMeters ?: prev.altitudeMeters,
            navigation = prev.navigation.copy(
                gpsAccuracyMeters = motion.horizontalAccuracyMeters,
                gpsGrade = TripMotionPolicy.gpsAccuracyGrade(motion.horizontalAccuracyMeters),
                courseDegrees = if (moving) motion.bearingDegrees else null,
            ),
        )
    }

    private fun CoroutineScope.observeCompassTelemetry(currentState: MutableStateFlow<TouristDashboardState>) {
        launch {
            orientationReadingSource.readings().collectLatest { compass ->
                currentState.update { prev ->
                    val declination = prev.navigation.declinationDegrees
                    prev.copy(
                        azimuthDegrees = compass.azimuthDegrees,
                        compassAccuracy = compass.accuracy,
                        altitudeMeters = prev.altitudeMeters ?: compass.altitudeMeters,
                        navigation = prev.navigation.copy(
                            trueAzimuthDegrees = declination?.let {
                                (compass.azimuthDegrees + it + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun CoroutineScope.observeStepTelemetry(
        currentState: MutableStateFlow<TouristDashboardState>,
        stepsAvailable: Boolean,
    ) {
        if (!stepsAvailable) return
        launch {
            val window = ArrayDeque<StepReading>()
            stepCountReadingSource.readings().collect { stepReading ->
                if (baseStepCount == null) {
                    baseStepCount = stepReading.stepsSinceBoot
                }
                val currentBase = baseStepCount ?: stepReading.stepsSinceBoot
                val sessionSteps = (stepReading.stepsSinceBoot - currentBase).coerceAtLeast(0L)
                window.addLast(stepReading)
                while (stepReading.takenAtMillis - window.first().takenAtMillis > CADENCE_WINDOW_MS) {
                    window.removeFirst()
                }
                currentState.update {
                    it.copy(
                        stepsCount = sessionSteps,
                        trip = it.trip.copy(cadenceStepsPerMinute = cadence(window)),
                    )
                }
            }
        }
    }

    /** Steps per minute over the window; null until it spans enough time to mean anything. */
    private fun cadence(window: ArrayDeque<StepReading>): Int? {
        val first = window.firstOrNull()
        val last = window.lastOrNull()
        val span = if (first != null && last != null) last.takenAtMillis - first.takenAtMillis else 0L
        return if (first == null || last == null || span < MIN_CADENCE_SPAN_MS) {
            null
        } else {
            ((last.stepsSinceBoot - first.stepsSinceBoot) * TimeUnit.MINUTES.toMillis(1) / span).toInt()
        }
    }

    private fun CoroutineScope.observeGnssTelemetry(currentState: MutableStateFlow<TouristDashboardState>) {
        launch {
            gnssStatusDataSource.observe().collectLatest { section ->
                val snapshot = section.data
                if (snapshot != null) {
                    val totalSats = snapshot.satellites.size
                    val usedSats = snapshot.satellites.count { it.usedInFix }
                    val coord = snapshot.coordinate

                    val solar = if (coord != null) {
                        SolarCalculator.calculateSunriseSunset(
                            latitude = coord.latitudeDegrees,
                            longitude = coord.longitudeDegrees,
                        )
                    } else {
                        null
                    }

                    currentState.update { prev ->
                        val declination = coord?.let {
                            environmentTelemetry.declinationDegrees(
                                latitude = it.latitudeDegrees,
                                longitude = it.longitudeDegrees,
                                altitudeMeters = prev.altitudeMeters,
                                atMillis = snapshot.takenAtMillis,
                            )
                        }
                        prev.copy(
                            satellitesTotal = totalSats,
                            satellitesUsed = usedSats,
                            latitude = coord?.latitudeDegrees ?: prev.latitude,
                            longitude = coord?.longitudeDegrees ?: prev.longitude,
                            sunriseMillis = solar?.sunriseMillis ?: prev.sunriseMillis,
                            sunsetMillis = solar?.sunsetMillis ?: prev.sunsetMillis,
                            isDaylight = solar?.isDaylight ?: prev.isDaylight,
                            navigation = prev.navigation.copy(
                                declinationDegrees = declination ?: prev.navigation.declinationDegrees,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * S4068: the phone has no ambient temperature or humidity sensor, so the weather tiles are fed from
     * the Open-Meteo repository at the current fix. The regular period matches the repository's cache
     * TTL: a shorter one would only re-read the cache. S4145: after a failed fetch the next attempt comes
     * after [WEATHER_RETRY_MS] instead, so a first fix taken indoors does not leave the tiles empty for
     * twenty minutes.
     */
    private fun CoroutineScope.observeWeatherTelemetry(
        currentState: MutableStateFlow<TouristDashboardState>,
        seaLevelHpa: MutableStateFlow<Double?>,
    ) {
        launch {
            // Awaited, not polled: indoors the first fix may take minutes, and without it there is no place.
            currentState.first { it.latitude != null && it.longitude != null }
            while (isActive) {
                val state = currentState.value
                val latitude = state.latitude
                val longitude = state.longitude
                val nextDelay = if (latitude != null && longitude != null) {
                    val location = WeatherLocation(latitude, longitude, WEATHER_LOCATION_LABEL)
                    applyWeather(currentState, seaLevelHpa, weatherRepository.current(location))
                } else {
                    WEATHER_RETRY_MS
                }
                delay(nextDelay)
            }
        }
    }

    /**
     * [WeatherResult.Unavailable] keeps the previous reading: a blank tile reads as broken, an old one does
     * not. Returns how long to wait before the next fetch.
     */
    private fun applyWeather(
        currentState: MutableStateFlow<TouristDashboardState>,
        seaLevelHpa: MutableStateFlow<Double?>,
        result: WeatherResult,
    ): Long {
        val snapshot = when (result) {
            is WeatherResult.Fresh -> result.snapshot
            is WeatherResult.Stale -> result.snapshot
            WeatherResult.Unavailable -> null
        }
        if (snapshot != null) {
            val details = snapshot.details
            seaLevelHpa.value = details.pressureMslHpa ?: seaLevelHpa.value
            currentState.update { prev ->
                prev.copy(
                    temperatureCelsius = snapshot.temperature.toCelsius(snapshot.unit).toFloat(),
                    dewPointCelsius = snapshot.dewPoint?.toCelsius(snapshot.unit)?.toFloat() ?: prev.dewPointCelsius,
                    weatherCondition = snapshot.condition,
                    weatherDetails = TouristWeatherDetails(
                        apparentTemperatureCelsius = details.apparentTemperature?.toCelsius(snapshot.unit)?.toFloat(),
                        humidityPercent = details.relativeHumidityPercent,
                        windSpeedKmh = details.windSpeedKmh,
                        windGustsKmh = details.windGustsKmh,
                        windDirectionDegrees = details.windDirectionDegrees,
                        uvIndex = details.uvIndex,
                        precipitationProbabilityPercent = details.precipitationProbabilityPercent,
                        cloudCoverPercent = details.cloudCoverPercent,
                        visibilityMetres = details.visibilityMetres,
                    ),
                )
            }
        }
        return if (result is WeatherResult.Fresh) WEATHER_REFRESH_MS else WEATHER_RETRY_MS
    }

    /** A per-subscription holder; local to one collection of the dashboard flow, never exposed. */
    private fun <T> mutableSlot(initial: T): MutableStateFlow<T> = MutableStateFlow(initial)

    // The repository answers in the user's scale; the tile formatter converts from Celsius on its own.
    private fun Double.toCelsius(unit: WeatherUnit): Double = when (unit) {
        WeatherUnit.CELSIUS -> this
        WeatherUnit.FAHRENHEIT -> UnitScale.fahrenheitToCelsius(this)
    }

    private companion object {
        private const val UPDATE_THROTTLE_MS = 500L
        private const val CLOCK_TICK_MS = 1_000L
        private const val FULL_CIRCLE_DEGREES = 360f
        private const val WEATHER_LOCATION_LABEL = "tourist"
        private val WEATHER_REFRESH_MS = TimeUnit.MINUTES.toMillis(20)
        private val WEATHER_RETRY_MS = TimeUnit.MINUTES.toMillis(2)
        private val CADENCE_WINDOW_MS = TimeUnit.SECONDS.toMillis(60)
        private val MIN_CADENCE_SPAN_MS = TimeUnit.SECONDS.toMillis(10)
    }
}
