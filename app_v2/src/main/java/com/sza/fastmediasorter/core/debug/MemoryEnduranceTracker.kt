package com.sza.fastmediasorter.core.debug

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber

/**
 * Debug-only memory endurance tracker for S0120.
 *
 * Emits structured Logcat lines prefixed with `MEM_ENDURANCE |` at each checkpoint.
 * All methods are no-ops in release builds.
 *
 * Usage:
 * - Call [startScenario] when the endurance scenario begins.
 * - Call [checkpoint] at each transition point.
 * - Call [endScenario] when the scenario stops.
 * - [endScenario] automatically schedules [cooldownCheckpoint] 30 s later.
 *
 * Classification rule (applied per CYCLE_END checkpoint):
 * - PLATEAU: |delta| < 15%
 * - SUSPICIOUS: delta 15–40%
 * - FAIL: delta > 40% OR monotonic growth for 5+ consecutive cycles
 */
object MemoryEnduranceTracker {

    private const val COOLDOWN_DELAY_MS = 30_000L
    private const val PLATEAU_THRESHOLD = 15.0
    private const val SUSPICIOUS_THRESHOLD = 40.0
    private const val MONOTONIC_FAIL_COUNT = 5

    private var scenarioId: String = ""
    private var startElapsed: Long = 0L
    private var baselineHeapMb: Long = 0L
    private var peakHeapMb: Long = 0L
    private var transitions: Int = 0
    private var lastCycleHeapMb: Long = 0L
    private var monotonicGrowthCount: Int = 0
    private var cycleCount: Int = 0

    private val handler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------

    /** Start a named endurance scenario. Resets all internal state. */
    fun startScenario(id: String) {
        if (!BuildConfig.DEBUG) return
        scenarioId = id
        transitions = 0
        cycleCount = 0
        monotonicGrowthCount = 0
        peakHeapMb = 0L
        lastCycleHeapMb = 0L
        startElapsed = SystemClock.elapsedRealtime()
        baselineHeapMb = heapUsedMb()
        logCheckpoint("BASELINE")
    }

    /**
     * Record a named checkpoint within the active scenario.
     *
     * If no scenario is active, auto-starts one with [autoScenarioId].
     * Checkpoint labels matching "CYCLE_END" trigger delta classification.
     * All other labels are logged as-is.
     *
     * @param label      Checkpoint name (e.g. "TRANSITION", "SORT_CHANGE", "CYCLE_END").
     * @param autoScenarioId Scenario id to use when auto-starting; ignored if a scenario is active.
     */
    fun checkpoint(label: String, autoScenarioId: String = "AUTO") {
        if (!BuildConfig.DEBUG) return
        if (scenarioId.isEmpty()) startScenario(autoScenarioId)
        transitions++
        val heapMb = heapUsedMb()
        if (heapMb > peakHeapMb) peakHeapMb = heapMb
        logCheckpoint(label)
        if (label == "CYCLE_END") evaluateCycleDelta(heapMb)
    }

    /**
     * End the scenario. Logs SCENARIO_END, emits summary, schedules cooldown checkpoint.
     */
    fun endScenario() {
        if (!BuildConfig.DEBUG) return
        val finalHeap = heapUsedMb()
        if (finalHeap > peakHeapMb) peakHeapMb = finalHeap
        logCheckpoint("SCENARIO_END")
        val verdict = deriveVerdict(finalHeap)
        Timber.d(
            "MEM_ENDURANCE | SUMMARY | scenario=$scenarioId" +
                " | total_transitions=$transitions" +
                " | baseline=${baselineHeapMb}MB" +
                " | peak=${peakHeapMb}MB" +
                " | final=${finalHeap}MB" +
                " | verdict=$verdict"
        )
        handler.removeCallbacksAndMessages(COOLDOWN_TAG)
        handler.postDelayed({ cooldownCheckpoint() }, COOLDOWN_TAG, COOLDOWN_DELAY_MS)
    }

    /** Record the memory state 30 s after endScenario. Called automatically by endScenario. */
    fun cooldownCheckpoint() {
        if (!BuildConfig.DEBUG) return
        logCheckpoint("COOLDOWN_END")
        val cooldownHeap = heapUsedMb()
        val recovery = if (baselineHeapMb > 0) {
            ((cooldownHeap - baselineHeapMb) * 100.0 / baselineHeapMb).toInt()
        } else 0
        Timber.d(
            "MEM_ENDURANCE | COOLDOWN_RESULT | scenario=$scenarioId" +
                " | cooldown=${cooldownHeap}MB" +
                " | baseline=${baselineHeapMb}MB" +
                " | drift_from_baseline=${recovery}%"
        )
    }

    // -------------------------------------------------------------------------

    private fun logCheckpoint(label: String) {
        val heapMb = heapUsedMb()
        val nativeMb = nativeHeapMb()
        val heapMaxMb = Runtime.getRuntime().maxMemory() / 1_048_576
        val elapsed = SystemClock.elapsedRealtime() - startElapsed
        Timber.d(
            "MEM_ENDURANCE | scenario=$scenarioId" +
                " | checkpoint=$label" +
                " | transitions=$transitions" +
                " | heapUsed=${heapMb}MB" +
                " | heapMax=${heapMaxMb}MB" +
                " | nativeAlloc=${nativeMb}MB" +
                " | elapsedMs=$elapsed"
        )
    }

    private fun evaluateCycleDelta(currentHeapMb: Long) {
        cycleCount++
        if (lastCycleHeapMb == 0L) {
            lastCycleHeapMb = currentHeapMb
            return
        }
        val delta = if (lastCycleHeapMb > 0) {
            (currentHeapMb - lastCycleHeapMb) * 100.0 / lastCycleHeapMb
        } else 0.0
        if (delta > 0) monotonicGrowthCount++ else monotonicGrowthCount = 0
        val classification = when {
            delta > SUSPICIOUS_THRESHOLD || monotonicGrowthCount >= MONOTONIC_FAIL_COUNT -> "FAIL"
            delta >= PLATEAU_THRESHOLD -> "SUSPICIOUS"
            else -> "PLATEAU"
        }
        Timber.d(
            "MEM_ENDURANCE | scenario=$scenarioId" +
                " | cycle=$cycleCount" +
                " | cycle_delta=${"%.1f".format(delta)}%" +
                " | classification=$classification"
        )
        lastCycleHeapMb = currentHeapMb
    }

    private fun deriveVerdict(finalHeapMb: Long): String {
        // If cooldown would return within 20% of baseline → always PLATEAU
        val projectedDrift = if (baselineHeapMb > 0) {
            (finalHeapMb - baselineHeapMb) * 100.0 / baselineHeapMb
        } else 0.0
        return when {
            monotonicGrowthCount >= MONOTONIC_FAIL_COUNT -> "FAIL"
            projectedDrift > SUSPICIOUS_THRESHOLD -> "FAIL"
            projectedDrift in PLATEAU_THRESHOLD..SUSPICIOUS_THRESHOLD -> "SUSPICIOUS"
            else -> "PLATEAU"
        }
    }

    private fun heapUsedMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1_048_576
    }

    private fun nativeHeapMb(): Long = Debug.getNativeHeapAllocatedSize() / 1_048_576

    // Token used to cancel pending cooldown callbacks.
    private val COOLDOWN_TAG = Any()
}
