package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.data.sensors.EnvironmentSensorReadingSource
import com.sza.fastmediasorter.data.sensors.MagneticDeclinationSource
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import com.sza.fastmediasorter.domain.model.tourist.ElevationSource
import com.sza.fastmediasorter.domain.model.tourist.PressureSample
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S4145: the barometer, light, magnetic-field and tilt feeds of the Tourist dashboard.
 *
 * Split from [ObserveTouristDashboardUseCase] so the position and weather wiring stays readable; every
 * collector here is launched in the dashboard flow's own scope and dies with it (strategic ADR-1).
 */
class TouristEnvironmentTelemetry @Inject constructor(
    private val environmentSensorReadingSource: EnvironmentSensorReadingSource,
    private val magneticDeclinationSource: MagneticDeclinationSource,
    private val tripTracker: TouristTripTracker,
) {

    /** The last GPS altitude with its vertical accuracy - the barometer's second-best anchor. */
    data class GpsAltitudeFix(
        val altitudeMeters: Double,
        val verticalAccuracyMeters: Float?,
    )

    fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, atMillis: Long): Float =
        magneticDeclinationSource.declinationDegrees(latitude, longitude, altitudeMeters, atMillis)

    fun launchIn(
        scope: CoroutineScope,
        state: MutableStateFlow<TouristDashboardState>,
        seaLevelHpa: StateFlow<Double?>,
        gpsFix: StateFlow<GpsAltitudeFix?>,
    ) {
        observePressure(scope, state, seaLevelHpa, gpsFix)
        observeLight(scope, state)
        observeMagneticField(scope, state)
        observeLevel(scope, state)
    }

    private fun observePressure(
        scope: CoroutineScope,
        state: MutableStateFlow<TouristDashboardState>,
        seaLevelHpa: StateFlow<Double?>,
        gpsFix: StateFlow<GpsAltitudeFix?>,
    ) {
        scope.launch {
            environmentSensorReadingSource.pressure().collect { reading ->
                tripTracker.onPressure(PressureSample(reading.takenAtMillis, reading.hectopascals))
                val fix = gpsFix.value
                val baro = tripTracker.baroAltitude(
                    pressureHpa = reading.hectopascals,
                    seaLevelHpa = seaLevelHpa.value,
                    gpsAltitudeM = fix?.altitudeMeters,
                    gpsVerticalAccuracyM = fix?.verticalAccuracyMeters,
                )
                tripTracker.onElevation(baro.metres, ElevationSource.BAROMETER)
                state.update { prev ->
                    prev.copy(
                        pressure = prev.pressure.copy(
                            hectopascals = reading.hectopascals,
                            trend = tripTracker.pressureTrend(reading.takenAtMillis),
                            baroAltitudeMeters = baro.metres,
                            baroApproximate = baro.approximate,
                        ),
                    )
                }
                tripTracker.persistIfDue(reading.takenAtMillis)
            }
        }
    }

    private fun observeLight(scope: CoroutineScope, state: MutableStateFlow<TouristDashboardState>) {
        scope.launch {
            environmentSensorReadingSource.light().collect { reading ->
                state.update { it.copy(environment = it.environment.copy(lux = reading.lux)) }
            }
        }
    }

    /**
     * Earth's field is roughly 25-65 µT everywhere; a reading outside that band, or a compass the
     * platform itself distrusts, means something nearby is bending the needle.
     */
    private fun observeMagneticField(scope: CoroutineScope, state: MutableStateFlow<TouristDashboardState>) {
        scope.launch {
            environmentSensorReadingSource.magneticField().collect { reading ->
                state.update { prev ->
                    val outOfBand = reading.microtesla !in EARTH_FIELD_MIN_UT..EARTH_FIELD_MAX_UT
                    val distrusted = prev.compassAccuracy == SensorAccuracy.LOW ||
                        prev.compassAccuracy == SensorAccuracy.UNRELIABLE
                    prev.copy(
                        environment = prev.environment.copy(
                            magneticMicrotesla = reading.microtesla,
                            magneticInterference = outOfBand || distrusted,
                        ),
                    )
                }
            }
        }
    }

    private fun observeLevel(scope: CoroutineScope, state: MutableStateFlow<TouristDashboardState>) {
        scope.launch {
            environmentSensorReadingSource.level().collect { reading ->
                state.update { prev ->
                    prev.copy(
                        navigation = prev.navigation.copy(
                            pitchDegrees = reading.pitchDegrees,
                            rollDegrees = reading.rollDegrees,
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        const val EARTH_FIELD_MIN_UT = 25f
        const val EARTH_FIELD_MAX_UT = 65f
    }
}
