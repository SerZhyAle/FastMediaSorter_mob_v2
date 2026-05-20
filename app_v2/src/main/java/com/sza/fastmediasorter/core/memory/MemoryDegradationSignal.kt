package com.sza.fastmediasorter.core.memory

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Snapshot of a memory-degradation event emitted by [MemoryEnduranceTracker].
 *
 * @param scenario      Tracker scenario id (e.g. "VID-playback", "AUD-playback").
 * @param peakHeapMb    Peak Java heap usage observed during the scenario, in megabytes.
 * @param driftPercent  Percentage drift from baseline measured at COOLDOWN_RESULT; 0 if unavailable.
 */
data class MemoryDegradationEvent(
    val scenario: String,
    val peakHeapMb: Int,
    val driftPercent: Int,
)

/**
 * Release-safe signal channel for memory-degradation events (S0213 §5.1 Pillar C).
 *
 * The producer is [MemoryEnduranceTracker], which emits when its verdict reaches FAIL (or when
 * drift_from_baseline ≥ 50 % at cooldown). The consumer is `PlayerActivity`, which collects
 * the flow and shows a one-shot snackbar with a "Close player" action.
 *
 * The signal is deliberately decoupled from `BuildConfig.DEBUG` - the tracker's verbose Timber
 * output remains DEBUG-only, but the user-facing event must fire in release builds too, since
 * that is where the OOM crash matters most.
 */
interface MemoryDegradationSignal {

    /** Hot flow of degradation events; consumers should collect on a lifecycle-aware scope. */
    val events: SharedFlow<MemoryDegradationEvent>

    /** Producer entry: emit a FAIL-verdict / high-drift event. Non-suspending; drops on overflow. */
    fun emitFail(scenario: String, peakHeapMb: Int, driftPercent: Int)
}

/**
 * Process-scoped implementation. Backed by a [MutableSharedFlow] with a small replay buffer of 0
 * (events are momentary) and an extra capacity of 4 so the producer never suspends.
 */
@Singleton
class MemoryDegradationSignalImpl @Inject constructor() : MemoryDegradationSignal {

    private val _events = MutableSharedFlow<MemoryDegradationEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )

    override val events: SharedFlow<MemoryDegradationEvent> = _events.asSharedFlow()

    override fun emitFail(scenario: String, peakHeapMb: Int, driftPercent: Int) {
        _events.tryEmit(MemoryDegradationEvent(scenario, peakHeapMb, driftPercent))
    }
}
