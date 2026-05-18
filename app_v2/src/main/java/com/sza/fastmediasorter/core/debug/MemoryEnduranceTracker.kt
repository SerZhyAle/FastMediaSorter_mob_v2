package com.sza.fastmediasorter.core.debug

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.os.HandlerCompat
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.memory.MemoryDegradationSignal
import timber.log.Timber

/**
 * Memory endurance tracker for S0120 + S0213 Pillar C.
 *
 * Emits structured Logcat lines prefixed with `MEM_ENDURANCE |` at each checkpoint
 * (verbose logging is `BuildConfig.DEBUG`-only; state tracking and verdict emission are always on).
 * On verdict FAIL - when drift_from_baseline ≥ [DRIFT_FAIL_THRESHOLD] at cooldown AND the process
 * heap is currently under actual pressure ([isHeapUnderPressure]) - the tracker forwards a one-shot
 * event to the wired [MemoryDegradationSignal] so the UI layer can react (release-safe: needed in
 * production to drive the "Close player" snackbar from S0213 §5.1 Pillar C).
 *
 * Three conditions must hold simultaneously: a FAIL verdict at SCENARIO_END, high drift at
 * COOLDOWN_RESULT, AND heap occupancy ≥ [HEAP_PRESSURE_THRESHOLD]. The third gate distinguishes
 * "leak signal" from "user-facing memory danger": relative drift from a thin baseline (e.g. fresh
 * ExoPlayer scenario on Quest 3 with 30-50 MB baseline, growing to 80 MB during normal playback)
 * is a legitimate telemetry signal but does NOT mean the user is close to OOM - heap occupancy
 * relative to [Runtime.maxMemory] is the right signal for that.
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

    /** Drift threshold (percent above baseline) that triggers a degradation event at cooldown. */
    private const val DRIFT_FAIL_THRESHOLD = 50

    /**
     * Heap occupancy (used / maxMemory) above which the process is considered actually under
     * memory pressure. The leak-detection thresholds (FAIL verdict, drift ≥ 50%) measure direction
     * of change; this third gate measures absolute headroom remaining. Both kinds of signal must
     * agree before the user is shown the "Close player" snackbar - otherwise a fresh playback
     * session on a device with a small per-process heap (Quest 3, canonical emulator) trips the
     * verdict on transient growth even though the process has plenty of room left.
     */
    private const val HEAP_PRESSURE_THRESHOLD = 0.75

    /**
     * S0213 Pillar C: release-safe signal channel for FAIL verdicts.
     * Wired once from [com.sza.fastmediasorter.FastMediaSorterApp.onCreate]; null until then.
     */
    @Volatile
    private var degradationSignal: MemoryDegradationSignal? = null

    /** Wire the release-safe signal channel from Hilt-aware context (Application#onCreate). */
    fun wireDegradationSignal(signal: MemoryDegradationSignal) {
        degradationSignal = signal
    }

    private var scenarioId: String = ""
    private var startElapsed: Long = 0L
    private var baselineHeapMb: Long = 0L
    private var peakHeapMb: Long = 0L
    private var transitions: Int = 0
    private var lastCycleHeapMb: Long = 0L
    private var monotonicGrowthCount: Int = 0
    private var cycleCount: Int = 0

    /**
     * Verdict from the most recent [endScenario] call.
     * Stored so [cooldownCheckpoint] can guard its [MemoryDegradationSignal] emit on FAIL-only
     * (S0213 §5.1 Pillar C fix: high drift alone must not trigger the snackbar).
     */
    private var lastScenarioVerdict: String = ""

    private val handler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------

    /** Start a named endurance scenario. Resets all internal state. */
    fun startScenario(id: String) {
        // S0213 Pillar C: state tracking runs in all builds so endScenario can produce a verdict
        // and emit the release-safe degradation signal. Verbose Timber output stays DEBUG-only
        // (see [logCheckpoint] / [emitVerbose]).
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
        val finalHeap = heapUsedMb()
        if (finalHeap > peakHeapMb) peakHeapMb = finalHeap
        logCheckpoint("SCENARIO_END")
        val verdict = deriveVerdict(finalHeap)
        if (BuildConfig.DEBUG) {
            Timber.d(
                "MEM_ENDURANCE | SUMMARY | scenario=$scenarioId" +
                    " | total_transitions=$transitions" +
                    " | baseline=${baselineHeapMb}MB" +
                    " | peak=${peakHeapMb}MB" +
                    " | final=${finalHeap}MB" +
                    " | verdict=$verdict"
            )
        }
        // S0213 Pillar C: release-safe forwarding to the UI layer. Drift at SCENARIO_END is not
        // yet known (computed only in COOLDOWN_RESULT) - pass 0; the cooldown-result branch will
        // emit a second event with the actual drift value if the threshold is crossed.
        // The emit is additionally gated by [isHeapUnderPressure]: a FAIL verdict on its own
        // describes growth-from-baseline, NOT proximity-to-OOM. The user-facing snackbar must
        // only fire when the heap is actually close to its limit; otherwise capable devices with
        // a 512 MB per-process heap see false alarms on every normal playback session.
        lastScenarioVerdict = verdict
        if (verdict == "FAIL" && isHeapUnderPressure(emitContext = "SCENARIO_END")) {
            degradationSignal?.emitFail(scenarioId, peakHeapMb.toInt(), driftPercent = 0)
        }
        handler.removeCallbacksAndMessages(COOLDOWN_TAG)
        HandlerCompat.postDelayed(handler, { cooldownCheckpoint() }, COOLDOWN_TAG, COOLDOWN_DELAY_MS)
    }

    /** Record the memory state 30 s after endScenario. Called automatically by endScenario. */
    fun cooldownCheckpoint() {
        logCheckpoint("COOLDOWN_END")
        val cooldownHeap = heapUsedMb()
        val recovery = if (baselineHeapMb > 0) {
            ((cooldownHeap - baselineHeapMb) * 100.0 / baselineHeapMb).toInt()
        } else 0
        if (BuildConfig.DEBUG) {
            Timber.d(
                "MEM_ENDURANCE | COOLDOWN_RESULT | scenario=$scenarioId" +
                    " | cooldown=${cooldownHeap}MB" +
                    " | baseline=${baselineHeapMb}MB" +
                    " | drift_from_baseline=${recovery}%"
            )
        }
        // S0213 Pillar C: three conditions must hold - FAIL verdict at SCENARIO_END, high drift
        // at COOLDOWN_RESULT, AND the heap is currently under actual pressure ([isHeapUnderPressure]).
        // Drift alone (SUSPICIOUS/PLATEAU) does not trigger the snackbar; transient native heap
        // growth during normal video playback reaches 50%+ without a real leak. The occupancy gate
        // additionally prevents false alarms on capable devices with a small per-process heap
        // (Quest 3, canonical emulator) where percent-drift from a thin baseline is a noisy signal.
        if (lastScenarioVerdict == "FAIL" &&
            recovery >= DRIFT_FAIL_THRESHOLD &&
            isHeapUnderPressure(emitContext = "COOLDOWN_RESULT")
        ) {
            degradationSignal?.emitFail(scenarioId, peakHeapMb.toInt(), driftPercent = recovery)
        }
    }

    // -------------------------------------------------------------------------

    private fun logCheckpoint(label: String) {
        // Verbose Timber output only in DEBUG - state mutation already happened in the caller.
        if (!BuildConfig.DEBUG) return
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
        if (BuildConfig.DEBUG) {
            Timber.d(
                "MEM_ENDURANCE | scenario=$scenarioId" +
                    " | cycle=$cycleCount" +
                    " | cycle_delta=${"%.1f".format(delta)}%" +
                    " | classification=$classification"
            )
        }
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

    /**
     * Return true when the current Java heap occupancy (used / max) is at or above
     * [HEAP_PRESSURE_THRESHOLD]. Used as the third gate on the user-facing "Close player"
     * snackbar - keeps leak-detection telemetry decoupled from headroom signalling.
     *
     * `emitContext` is purely for the diagnostic Timber line; it identifies which call site
     * (SCENARIO_END / COOLDOWN_RESULT) ran the check.
     */
    private fun isHeapUnderPressure(emitContext: String): Boolean {
        val rt = Runtime.getRuntime()
        val maxMb = rt.maxMemory() / 1_048_576
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
        val occupancy = if (maxMb > 0) usedMb.toDouble() / maxMb.toDouble() else 0.0
        val underPressure = occupancy >= HEAP_PRESSURE_THRESHOLD
        if (BuildConfig.DEBUG) {
            Timber.d(
                "MEM_ENDURANCE | PRESSURE_CHECK | context=$emitContext" +
                    " | used=${usedMb}MB / max=${maxMb}MB" +
                    " | occupancy=${"%.2f".format(occupancy)}" +
                    " | threshold=${"%.2f".format(HEAP_PRESSURE_THRESHOLD)}" +
                    " | underPressure=$underPressure"
            )
        }
        return underPressure
    }

    // Token used to cancel pending cooldown callbacks.
    private val COOLDOWN_TAG = Any()
}
