package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.domain.model.tourist.ElevationSource
import com.sza.fastmediasorter.domain.model.tourist.PressureSample
import com.sza.fastmediasorter.domain.model.tourist.PressureTrendResult
import com.sza.fastmediasorter.domain.model.tourist.TouristTripSnapshot
import com.sza.fastmediasorter.domain.repository.TouristTripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4145: owns the one Tourist trip of the process - its accumulation, its persistence and its reset.
 *
 * A singleton because the trip outlives one dashboard subscription: leaving the screen and coming back
 * continues the same trip, and the persisted copy carries it across a process restart (strategic ADR-2).
 */
@Singleton
class TouristTripTracker @Inject constructor(
    private val repository: TouristTripRepository,
) {

    private val accumulator = TripAccumulator()
    private val trendCalculator = PressureTrendCalculator()
    private val calibrator = BarometricAltitudeCalibrator()
    private val loadMutex = Mutex()
    private val state = MutableStateFlow(TouristTripSnapshot.EMPTY)
    private val slopeWindow = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())

    @Volatile
    private var loaded = false

    @Volatile
    private var lastPersistedAtMillis = 0L

    val snapshot: StateFlow<TouristTripSnapshot> = state.asStateFlow()

    suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (!loaded) {
                state.value = repository.load()
                loaded = true
            }
        }
    }

    fun onMotion(speedKmh: Float?, distanceDeltaMeters: Double, horizontalAccuracyM: Float?, atMillis: Long) {
        state.update { accumulator.onMotion(it, speedKmh, distanceDeltaMeters, horizontalAccuracyM, atMillis) }
    }

    fun onElevation(metres: Double, source: ElevationSource) {
        val updated = state.updateAndGet { accumulator.onElevation(it, metres, source) }
        if (updated.elevationSource == source) {
            slopeWindow.update { (it + (updated.distanceMeters to metres)).takeLast(SLOPE_WINDOW_SIZE) }
        }
    }

    fun onPressure(sample: PressureSample) {
        state.update { it.copy(pressureSamples = trendCalculator.append(it.pressureSamples, sample)) }
    }

    fun pressureTrend(nowMillis: Long): PressureTrendResult =
        trendCalculator.trend(state.value.pressureSamples, nowMillis)

    fun slopePercent(): Float? = accumulator.slopePercent(slopeWindow.value)

    fun baroAltitude(
        pressureHpa: Float,
        seaLevelHpa: Double?,
        gpsAltitudeM: Double?,
        gpsVerticalAccuracyM: Float?,
    ): BarometricAltitudeCalibrator.BaroAltitude =
        calibrator.altitude(pressureHpa, seaLevelHpa, gpsAltitudeM, gpsVerticalAccuracyM)

    /** Called on every accepted sample; writes at most once per [PERSIST_INTERVAL_MS]. */
    suspend fun persistIfDue(nowMillis: Long) {
        if (nowMillis - lastPersistedAtMillis >= PERSIST_INTERVAL_MS) {
            persist(nowMillis)
        }
    }

    suspend fun persist(nowMillis: Long = System.currentTimeMillis()) {
        lastPersistedAtMillis = nowMillis
        repository.save(state.value)
    }

    suspend fun reset() {
        Timber.d("S4145: trip reset")
        state.update { accumulator.reset(it) }
        slopeWindow.value = emptyList()
        persist()
    }

    private companion object {
        const val SLOPE_WINDOW_SIZE = 30
        val PERSIST_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15)
    }
}
