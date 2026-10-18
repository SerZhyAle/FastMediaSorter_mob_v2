package com.sza.fastmediasorter.wear.domain.tourist

import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4146: owns the one watch Tourist trip of the process - its accumulation, its persistence and its
 * reset. The twin of `TouristTripTracker` in `app_v2` (S4145).
 *
 * A singleton because the trip outlives one screen subscription: leaving the section and coming back
 * continues the same trip, and the stored copy carries it across a process restart (strategic ADR-1).
 */
@Singleton
class WearTouristTripTracker @Inject constructor(
    private val preferences: WearPreferencesRepository,
) {

    private val accumulator = WearTripAccumulator()
    private val trendCalculator = WearPressureTrendCalculator()
    private val calibrator = WearBarometricAltitudeCalibrator()
    private val loadMutex = Mutex()
    private val persistMutex = Mutex()
    private val state = MutableStateFlow(WearTripSnapshot.EMPTY)
    private val slopeWindow = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())

    @Volatile
    private var loaded = false

    @Volatile
    private var lastPersistedAtMillis = 0L

    val snapshot: StateFlow<WearTripSnapshot> = state.asStateFlow()

    suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (!loaded) {
                state.value = WearTripSnapshotCodec.decode(preferences.touristTrip.first()) ?: WearTripSnapshot.EMPTY
                loaded = true
            }
        }
    }

    fun onMotion(speedKmh: Float?, distanceDeltaMeters: Double, horizontalAccuracyM: Float?, atMillis: Long) {
        state.update { accumulator.onMotion(it, speedKmh, distanceDeltaMeters, horizontalAccuracyM, atMillis) }
    }

    fun onElevation(metres: Double, source: WearElevationSource) {
        val updated = state.updateAndGet { accumulator.onElevation(it, metres, source) }
        if (updated.elevationSource == source) {
            slopeWindow.update { (it + (updated.distanceMeters to metres)).takeLast(SLOPE_WINDOW_SIZE) }
        }
    }

    fun onPressure(sample: WearPressureSample) {
        state.update { it.copy(pressureSamples = trendCalculator.append(it.pressureSamples, sample)) }
    }

    fun pressureTrend(nowMillis: Long): WearPressureTrendResult =
        trendCalculator.trend(state.value.pressureSamples, nowMillis)

    fun slopePercent(): Float? = accumulator.slopePercent(slopeWindow.value)

    fun baroAltitude(
        pressureHpa: Float,
        gpsAltitudeM: Double?,
        gpsVerticalAccuracyM: Float?,
        gpsAltitudeIsEllipsoid: Boolean,
    ): WearBaroAltitude = calibrator.altitude(
        pressureHpa = pressureHpa,
        seaLevelHpa = null,
        gpsAltitudeM = gpsAltitudeM,
        gpsVerticalAccuracyM = gpsVerticalAccuracyM,
        gpsAltitudeIsEllipsoid = gpsAltitudeIsEllipsoid,
    )

    /** Called on every accepted sample; writes at most once per [PERSIST_INTERVAL_MS]. */
    suspend fun persistIfDue(nowMillis: Long) {
        if (nowMillis - lastPersistedAtMillis >= PERSIST_INTERVAL_MS) {
            persist(nowMillis)
        }
    }

    suspend fun persist(nowMillis: Long = System.currentTimeMillis()) {
        // Never write before the stored trip was read: an empty in-memory trip would overwrite it.
        if (!loaded) return
        // Capture and write under one lock: a writer that captured the trip before a reset finishes its write
        // before the reset's own write captures, so the last write on disk is always the newest state.
        persistMutex.withLock {
            preferences.setTouristTrip(WearTripSnapshotCodec.encode(state.value))
            // After the write only: a failed write must not hold back the next attempt for a whole interval.
            lastPersistedAtMillis = maxOf(lastPersistedAtMillis, nowMillis)
        }
    }

    suspend fun reset() {
        ensureLoaded()
        state.update { accumulator.reset(it) }
        slopeWindow.value = emptyList()
        persist()
    }

    private companion object {
        const val SLOPE_WINDOW_SIZE = 30
        val PERSIST_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15)
    }
}
