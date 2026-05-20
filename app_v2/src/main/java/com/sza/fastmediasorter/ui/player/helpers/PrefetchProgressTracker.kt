package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.PrefetchPlan
import com.sza.fastmediasorter.domain.model.StreamViabilityState
import com.sza.fastmediasorter.domain.playback.PrefetchFormula
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Progress of pre-caching, sampled from `ExoPlayer.bufferedPosition - currentPosition`
 * and consumed by the player UI overlay.
 */
data class PrefetchProgress(
    val cachedSec: Int,
    val targetSec: Int,
    val isBuffering: Boolean,
    val sourceLabel: String,
    val viability: StreamViabilityState
) {
    companion object {
        val EMPTY = PrefetchProgress(
            cachedSec = 0,
            targetSec = 0,
            isBuffering = false,
            sourceLabel = "",
            viability = StreamViabilityState.VIABLE
        )
    }
}

/**
 * Escalation event emitted when stalls during the current session cross a threshold.
 * The caller recomputes the plan with the reported [ratioInflation] added to the
 * derivation's ratio and decides whether the new viability crosses into NOT_VIABLE.
 */
data class EscalationEvent(
    val stallCount: Int,
    val ratioInflation: Double,
    val crossesToNotViable: Boolean
)

/**
 * Counts buffering stalls during the current session and emits escalation events
 * when pre-defined thresholds are crossed. Also polls the attached player at ~4 Hz
 * to feed the pre-cache progress overlay.
 *
 * This class is pure Kotlin + coroutines: the [Player.Listener] methods delegate to
 * [markReady] and [recordStall], both of which are public so tests can drive the
 * state machine without instantiating an ExoPlayer.
 */
class PrefetchProgressTracker(
    private val scope: CoroutineScope
) : Player.Listener {

    private val _stallCount = MutableStateFlow(0)
    val stallCount: StateFlow<Int> = _stallCount.asStateFlow()

    private val _prefetchProgress = MutableStateFlow(PrefetchProgress.EMPTY)
    val prefetchProgress: StateFlow<PrefetchProgress> = _prefetchProgress.asStateFlow()

    private val _escalation = MutableSharedFlow<EscalationEvent>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val escalation: SharedFlow<EscalationEvent> = _escalation.asSharedFlow()

    private var hasReachedReady = false
    private var lastEmittedInflation = 0.0
    private var attachedPlayer: Player? = null
    private var plan: PrefetchPlan? = null
    private var pollJob: Job? = null

    /**
     * Start observing [player] using [plan]. Safe to call again with a new player
     * or a new plan - the previous polling job is cancelled first.
     */
    fun attach(player: Player, plan: PrefetchPlan) {
        detach()
        this.attachedPlayer = player
        this.plan = plan
        hasReachedReady = false
        lastEmittedInflation = 0.0
        _stallCount.value = 0
        _prefetchProgress.value = PrefetchProgress.EMPTY.copy(
            targetSec = plan.targetPrefetchSec,
            sourceLabel = plan.sourceLabel,
            viability = plan.viability
        )
        player.addListener(this)
    }

    /**
     * Replace the plan without restarting the tracker. Used when the formula is
     * recomputed mid-session (e.g., after `onTracksChanged` delivers a real bitrate,
     * or after escalation changes the target).
     */
    fun updatePlan(newPlan: PrefetchPlan) {
        plan = newPlan
        _prefetchProgress.value = _prefetchProgress.value.copy(
            targetSec = newPlan.targetPrefetchSec,
            sourceLabel = newPlan.sourceLabel,
            viability = newPlan.viability
        )
    }

    fun detach() {
        pollJob?.cancel()
        pollJob = null
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
        hasReachedReady = false
    }

    // ── Public entry points, exposed so unit tests can drive the state machine ──

    /**
     * Called when the player first reaches [Player.STATE_READY]. Starts the polling
     * loop that feeds [prefetchProgress]. Stalls recorded before this are ignored
     * (they are part of startup, not mid-session instability).
     */
    fun markReady() {
        if (hasReachedReady) return
        hasReachedReady = true
        startPolling()
    }

    /**
     * Record a mid-playback buffering stall and maybe emit an escalation event.
     * No-op until [markReady] has been called at least once.
     */
    fun recordStall() {
        if (!hasReachedReady) return
        val next = _stallCount.value + 1
        _stallCount.value = next
        maybeEscalate(next)
    }

    private fun maybeEscalate(stalls: Int) {
        val inflation = when {
            stalls >= STALL_HARD_THRESHOLD -> HARD_RATIO_INFLATION
            stalls >= STALL_SOFT_THRESHOLD -> SOFT_RATIO_INFLATION
            else -> 0.0
        }
        if (inflation <= lastEmittedInflation) return

        val currentPlan = plan ?: return
        val baseRatio = currentPlan.derivation.ratio
        val newViability = PrefetchFormula.classifyViability(baseRatio + inflation)
        val crosses = currentPlan.viability != StreamViabilityState.NOT_VIABLE &&
            newViability == StreamViabilityState.NOT_VIABLE

        lastEmittedInflation = inflation
        val emitted = _escalation.tryEmit(
            EscalationEvent(
                stallCount = stalls,
                ratioInflation = inflation,
                crossesToNotViable = crosses
            )
        )
        if (!emitted) {
            Timber.w("PrefetchProgressTracker: escalation buffer full - event dropped")
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (attachedPlayer != null) {
                val player = attachedPlayer ?: break
                val plan = plan ?: break
                val cachedMs = (player.bufferedPosition - player.currentPosition)
                    .coerceAtLeast(0L)
                val cachedSec = (cachedMs / 1000L).toInt()
                val isBuffering = player.playbackState == Player.STATE_BUFFERING
                _prefetchProgress.value = PrefetchProgress(
                    cachedSec = cachedSec,
                    targetSec = plan.targetPrefetchSec,
                    isBuffering = isBuffering,
                    sourceLabel = plan.sourceLabel,
                    viability = plan.viability
                )
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // ── Player.Listener ──

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> markReady()
            Player.STATE_BUFFERING -> recordStall()
            else -> Unit
        }
    }

    companion object {
        internal const val STALL_SOFT_THRESHOLD = 2
        internal const val STALL_HARD_THRESHOLD = 4
        internal const val SOFT_RATIO_INFLATION = 0.1
        internal const val HARD_RATIO_INFLATION = 0.2
        private const val POLL_INTERVAL_MS = 250L
    }
}
