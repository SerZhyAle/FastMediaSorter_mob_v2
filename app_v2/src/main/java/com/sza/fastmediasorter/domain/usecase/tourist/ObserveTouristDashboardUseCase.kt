package com.sza.fastmediasorter.domain.usecase.tourist

import com.sza.fastmediasorter.data.networkmonitor.GnssStatusDataSource
import com.sza.fastmediasorter.data.sensors.MotionReadingSource
import com.sza.fastmediasorter.data.sensors.OrientationReadingSource
import com.sza.fastmediasorter.data.sensors.StepCountReadingSource
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.util.SolarCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2922: combines sensor and GNSS feeds into a unified real-time [TouristDashboardState].
 */
@Singleton
class ObserveTouristDashboardUseCase @Inject constructor(
    private val motionReadingSource: MotionReadingSource,
    private val orientationReadingSource: OrientationReadingSource,
    private val stepCountReadingSource: StepCountReadingSource,
    private val gnssStatusDataSource: GnssStatusDataSource,
) {

    private val accumulatedTripMeters = MutableStateFlow(0.0)
    private var baseStepCount: Long? = null
    private var maxRecordedSpeed = 0f

    fun resetTripDistance() {
        accumulatedTripMeters.value = 0.0
        maxRecordedSpeed = 0f
        baseStepCount = null
    }

    operator fun invoke(initialFocus: TouristTileType = TouristTileType.SPEED): Flow<TouristDashboardState> = channelFlow {
        val currentState = MutableStateFlow(TouristDashboardState(focusedTile = initialFocus))

        launch {
            currentState.collectLatest { state ->
                send(state)
            }
        }

        launch {
            accumulatedTripMeters.collectLatest { tripDist ->
                currentState.update { it.copy(tripDistanceMeters = tripDist) }
            }
        }

        // Motion & Speed & Altitude
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

        // Compass / Azimuth
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

        // Steps
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

        // GNSS: Satellites, Coordinates, Solar times
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
                    } else null

                    currentState.update { prev ->
                        prev.copy(
                            satellitesTotal = totalSats,
                            satellitesUsed = usedSats,
                            latitude = coord?.latitudeDegrees ?: prev.latitude,
                            longitude = coord?.longitudeDegrees ?: prev.longitude,
                            sunriseMillis = solar?.sunriseMillis ?: prev.sunriseMillis,
                            sunsetMillis = solar?.sunsetMillis ?: prev.sunsetMillis,
                            isDaylight = solar?.isDaylight ?: prev.isDaylight,
                        )
                    }
                }
            }
        }
    }
}
