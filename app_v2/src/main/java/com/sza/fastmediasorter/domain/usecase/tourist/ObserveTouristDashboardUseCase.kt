package com.sza.fastmediasorter.domain.usecase.tourist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.networkmonitor.GnssStatusDataSource
import com.sza.fastmediasorter.data.sensors.MotionReadingSource
import com.sza.fastmediasorter.data.sensors.OrientationReadingSource
import com.sza.fastmediasorter.data.sensors.StepCountReadingSource
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.util.SolarCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * S2922/S3000: combines sensor and GNSS feeds into a unified real-time [TouristDashboardState].
 */
@Singleton
class ObserveTouristDashboardUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val motionReadingSource: MotionReadingSource,
    private val orientationReadingSource: OrientationReadingSource,
    private val stepCountReadingSource: StepCountReadingSource,
    private val gnssStatusDataSource: GnssStatusDataSource,
) {

    private val accumulatedTripMeters = MutableStateFlow(0.0)

    @Volatile
    private var baseStepCount: Long? = null

    @Volatile
    private var maxRecordedSpeed = 0f

    fun resetTripDistance() {
        accumulatedTripMeters.value = 0.0
    }

    fun resetMaxSpeedAndTrip() {
        maxRecordedSpeed = 0f
        accumulatedTripMeters.value = 0.0
    }

    fun resetSteps() {
        baseStepCount = null
    }

    @OptIn(FlowPreview::class)
    operator fun invoke(
        initialFocus: TouristTileType = TouristTileType.SPEED,
    ): Flow<TouristDashboardState> = channelFlow {
        val hasLoc = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val hasAct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val currentState = MutableStateFlow(
            TouristDashboardState(
                focusedTile = initialFocus,
                hasLocationPermission = hasLoc,
                hasActivityRecognitionPermission = hasAct,
            ),
        )

        launch {
            currentState
                .sample(UPDATE_THROTTLE_MS)
                .collectLatest { state ->
                    send(state)
                }
        }

        launch {
            accumulatedTripMeters.collectLatest { tripDist ->
                currentState.update { it.copy(tripDistanceMeters = tripDist) }
            }
        }

        observeMotionTelemetry(currentState)
        observeCompassTelemetry(currentState)
        observeStepTelemetry(currentState)
        observeGnssTelemetry(currentState)
    }

    private fun CoroutineScope.observeMotionTelemetry(currentState: MutableStateFlow<TouristDashboardState>) {
        launch {
            motionReadingSource.readings().collectLatest { motion ->
                if (motion.speedKmh != null && motion.speedKmh > maxRecordedSpeed) {
                    maxRecordedSpeed = motion.speedKmh
                }
                if (motion.distanceDeltaMeters > 0) {
                    accumulatedTripMeters.update { it + motion.distanceDeltaMeters }
                }
                currentState.update { prev ->
                    prev.copy(
                        speedKmh = motion.speedKmh,
                        maxSpeedKmh = maxRecordedSpeed,
                        altitudeMeters = motion.altitudeMeters ?: prev.altitudeMeters,
                    )
                }
            }
        }
    }

    private fun CoroutineScope.observeCompassTelemetry(currentState: MutableStateFlow<TouristDashboardState>) {
        launch {
            orientationReadingSource.readings().collectLatest { compass ->
                currentState.update { prev ->
                    prev.copy(
                        azimuthDegrees = compass.azimuthDegrees,
                        compassAccuracy = compass.accuracy,
                        altitudeMeters = prev.altitudeMeters ?: compass.altitudeMeters,
                    )
                }
            }
        }
    }

    private fun CoroutineScope.observeStepTelemetry(currentState: MutableStateFlow<TouristDashboardState>) {
        if (!BuildConfig.IS_NO_LEGAL_FLAVOR) return
        launch {
            stepCountReadingSource.readings().collectLatest { stepReading ->
                if (baseStepCount == null) {
                    baseStepCount = stepReading.stepsSinceBoot
                }
                val currentBase = baseStepCount ?: stepReading.stepsSinceBoot
                val sessionSteps = (stepReading.stepsSinceBoot - currentBase).coerceAtLeast(0L)
                currentState.update { it.copy(stepsCount = sessionSteps) }
            }
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
                        val temp = prev.temperatureCelsius
                        val hum = prev.humidityPercent
                        val dew = if (temp != null && hum != null) {
                            calculateDewPoint(temp, hum)
                        } else {
                            prev.dewPointCelsius
                        }

                        prev.copy(
                            satellitesTotal = totalSats,
                            satellitesUsed = usedSats,
                            latitude = coord?.latitudeDegrees ?: prev.latitude,
                            longitude = coord?.longitudeDegrees ?: prev.longitude,
                            sunriseMillis = solar?.sunriseMillis ?: prev.sunriseMillis,
                            sunsetMillis = solar?.sunsetMillis ?: prev.sunsetMillis,
                            isDaylight = solar?.isDaylight ?: prev.isDaylight,
                            dewPointCelsius = dew,
                        )
                    }
                }
            }
        }
    }

    private fun calculateDewPoint(tempCelsius: Float, humidityPercent: Float): Float {
        val alpha = ((MAGNUS_A * tempCelsius) / (MAGNUS_B + tempCelsius)) +
            ln(humidityPercent.coerceIn(HUMIDITY_MIN, HUMIDITY_MAX) / HUMIDITY_MAX)
        return (MAGNUS_B * alpha) / (MAGNUS_A - alpha)
    }

    private companion object {
        private const val UPDATE_THROTTLE_MS = 500L
        private const val MAGNUS_A = 17.27f
        private const val MAGNUS_B = 237.7f
        private const val HUMIDITY_MIN = 1f
        private const val HUMIDITY_MAX = 100.0f
    }
}
