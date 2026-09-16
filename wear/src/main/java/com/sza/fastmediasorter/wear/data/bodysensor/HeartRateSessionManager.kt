package com.sza.fastmediasorter.wear.data.bodysensor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.bodysensor.WearBodySensorDataSource
import com.sza.fastmediasorter.wear.domain.repository.HeartRateHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3112: one heart-rate session that lasts exactly as long as the watch app is in the foreground.
 *
 * The session is owned here rather than by the body-sensor screen's ViewModel because the owner asked
 * for a measurement that starts when the app opens and keeps updating until the app is left or the
 * watch screen goes off - a span no ViewModel survives (strategic spec ADR-2).
 *
 * The foreground is read through [Application.ActivityLifecycleCallbacks], matching the two callbacks
 * this module already registers, so the watch needs no `lifecycle-process` dependency (ADR-1). Health
 * Services only measures in the foreground anyway, so the started-activity count is the exact bound.
 *
 * In `standard` the injected data source is the withheld one: its flow answers `Unavailable` once and
 * completes, so a session there costs one emission and touches no sensor.
 */
@Singleton
class HeartRateSessionManager @Inject constructor(
    private val dataSource: WearBodySensorDataSource,
    private val historyRepository: HeartRateHistoryRepository
) : Application.ActivityLifecycleCallbacks {

    /**
     * Owned rather than injected, for the reason [com.sza.fastmediasorter.wear.data.power.WearPowerStateObserver]
     * states: this is a process-lifetime singleton, so there is nothing shorter-lived to cancel it against.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutableState = MutableStateFlow<BodySensorReading>(BodySensorReading.Idle)
    val state: StateFlow<BodySensorReading> = mutableState.asStateFlow()

    private var startedActivities = 0
    private var session: Job? = null

    /** Zero means nothing has been saved in this session yet, so the first sample is written at once. */
    private var lastSavedAtMillis = 0L

    /**
     * Starts the session over. Called after the user answers the permission dialog or presses the
     * retry action: the refusal that ended the previous session may no longer hold. Does nothing while
     * the app is in the background, where a measurement could not run.
     */
    fun restart() {
        if (startedActivities > 0) {
            startSession()
        }
    }

    private fun startSession() {
        // Cancelling first is what runs the previous flow's awaitClose, so the old sensor callback is
        // unregistered before a new one is registered rather than leaking alongside it.
        session?.cancel()
        lastSavedAtMillis = 0L
        Timber.d("S3112: heart-rate foreground session starting")
        session = scope.launch {
            dataSource.measure().collect { reading -> publish(reading) }
        }
    }

    private fun stopSession() {
        session?.cancel()
        session = null
        // A number from a session that has ended must not survive it: the next foreground entry starts
        // measuring again, and until its first sample the screen has nothing true to show.
        mutableState.value = BodySensorReading.Idle
        lastSavedAtMillis = 0L
    }

    private suspend fun publish(reading: BodySensorReading) {
        mutableState.value = reading
        if (reading is BodySensorReading.HeartRate) {
            saveIfDue(reading.beatsPerMinute)
        }
    }

    /**
     * Health Services delivers a sample every few seconds; one row per sample would fill the history
     * with a single minute of one session. The first sample of a session is always kept, so a short
     * look at the screen still leaves a trace.
     */
    private suspend fun saveIfDue(beatsPerMinute: Int) {
        val now = SystemClock.elapsedRealtime()
        val due = lastSavedAtMillis == 0L || now - lastSavedAtMillis >= SAVE_INTERVAL_MS
        if (due) {
            lastSavedAtMillis = now
            Timber.d("S3112: saving heart-rate sample to history")
            historyRepository.save(beatsPerMinute)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        if (startedActivities == 1) {
            startSession()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            stopSession()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        /** One row a minute: dense enough for a trend line, sparse enough for the watch's store. */
        const val SAVE_INTERVAL_MS = 60_000L
    }
}
