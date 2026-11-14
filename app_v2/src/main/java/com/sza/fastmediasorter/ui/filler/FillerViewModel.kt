package com.sza.fastmediasorter.ui.filler

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.filler.FILLER_MIN_RESERVE_BYTES
import com.sza.fastmediasorter.domain.filler.FILLER_RESERVE_BYTES
import com.sza.fastmediasorter.domain.filler.FillerEngine
import com.sza.fastmediasorter.domain.filler.FillerPlace
import com.sza.fastmediasorter.domain.filler.FillerProgressEstimator
import com.sza.fastmediasorter.domain.filler.FillerResult
import com.sza.fastmediasorter.domain.filler.FillerSession
import com.sza.fastmediasorter.domain.filler.FillerStorage
import com.sza.fastmediasorter.domain.filler.FillerTargetResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class FillerOutcome {
    DONE,
    CANCELLED,
    FAILED,
    CLEANUP_INCOMPLETE,
    CLEANUP_DONE,
    NOT_LOCAL,
    NO_ACCESS,
    MEASUREMENT_UNAVAILABLE,
    INSUFFICIENT_SPACE,
    BUSY,
}

data class FillerUiState(
    val place: FillerPlace? = null,
    val accessible: Boolean = false,
    val availableBytes: Long? = null,
    val running: Boolean = false,
    val plannedBytes: Long = 0,
    val writtenBytes: Long = 0,
    /** Null until the run has been observed long enough for a time to be honest. */
    val remainingMillis: Long? = null,
    val pending: FillerPlace? = null,
    val outcome: FillerOutcome? = null,
    val reserveBytes: Long = FILLER_RESERVE_BYTES,
) {
    val canStart: Boolean
        get() = !running && pending == null && accessible && (availableBytes ?: 0) > reserveBytes

    /** Free space can change while a run is going, so the share is clamped rather than trusted past 100. */
    val percentDone: Int
        get() = if (plannedBytes <= 0) {
            0
        } else {
            (writtenBytes * PERCENT_FULL / plannedBytes).toInt().coerceIn(0, PERCENT_FULL)
        }

    companion object {
        private const val PERCENT_FULL = 100

        /** The reserves the screen offers, lowest first; the engine itself accepts any value from its floor up. */
        val RESERVE_CHOICES_BYTES: List<Long> = listOf(
            FILLER_MIN_RESERVE_BYTES,
            25L shl 20,
            50L shl 20,
            100L shl 20,
            250L shl 20,
            500L shl 20,
            FILLER_RESERVE_BYTES,
        )
    }
}

/**
 * Owns the fill run so it survives rotation; leaving the screen cancels it, and the engine still
 * deletes what it wrote because its cleanup runs outside cancellation.
 */
@HiltViewModel
class FillerViewModel @Inject constructor(
    private val engine: FillerEngine,
    private val storage: FillerStorage,
    private val resolver: FillerTargetResolver,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FillerUiState())
    val state: StateFlow<FillerUiState> = mutableState.asStateFlow()

    private val estimator = FillerProgressEstimator()
    private var run: Job? = null
    private var pendingSession: FillerSession? = null
    private var selectionVersion = 0

    init {
        viewModelScope.launch {
            refreshPending()
            val defaultLocation = if (savedState.get<String>(KEY_LOCATION) == null) resolver.defaultLocation() else null
            (savedState.get<String>(KEY_LOCATION) ?: defaultLocation)?.let { select(it) }
        }
    }

    fun onFolderPicked(location: String) {
        viewModelScope.launch {
            resolver.grant(location)
            savedState[KEY_LOCATION] = location
            mutableState.update { it.copy(outcome = null) }
            select(location)
        }
    }

    /** Applies one of [FillerUiState.RESERVE_CHOICES_BYTES]; the value belongs to the next run only. */
    fun chooseReserve(bytes: Long) {
        Timber.d("S4567: chooseReserve bytes=$bytes running=${state.value.running}")
        if (bytes !in FillerUiState.RESERVE_CHOICES_BYTES) return
        mutableState.update {
            if (it.running) {
                it
            } else {
                it.copy(reserveBytes = bytes, outcome = outcomeForReserve(it, bytes))
            }
        }
    }

    fun start() {
        val current = state.value
        val place = current.place
        if (!current.canStart || place == null || run?.isActive == true) return
        val planned = (current.availableBytes ?: 0) - current.reserveBytes
        estimator.reset()
        estimator.record(0, nowMillis())
        mutableState.update {
            it.copy(running = true, plannedBytes = planned, writtenBytes = 0, remainingMillis = null, outcome = null)
        }
        run = viewModelScope.launch {
            var outcome = FillerOutcome.CANCELLED
            try {
                outcome = outcomeOf(
                    engine.run(place.target, current.reserveBytes) { written ->
                        estimator.record(written, nowMillis())
                        mutableState.update {
                            it.copy(writtenBytes = written, remainingMillis = estimator.remainingMillis(planned))
                        }
                    },
                )
            } finally {
                withContext(NonCancellable) { finishRun(place, outcome) }
            }
        }
    }

    fun cancel() {
        run?.cancel()
    }

    fun retryCleanup() {
        val session = pendingSession ?: return
        if (run?.isActive == true) return
        run = viewModelScope.launch {
            val cleaned = engine.retryCleanup(session)
            refreshPending()
            mutableState.update {
                it.copy(outcome = if (cleaned) FillerOutcome.CLEANUP_DONE else FillerOutcome.CLEANUP_INCOMPLETE)
            }
            state.value.place?.let { select(it.target.location) }
        }
    }

    private suspend fun finishRun(place: FillerPlace, outcome: FillerOutcome) {
        refreshPending()
        val residual = pendingSession != null
        val shown = if (residual && outcome != FillerOutcome.BUSY) FillerOutcome.CLEANUP_INCOMPLETE else outcome
        mutableState.update {
            it.copy(running = false, remainingMillis = null, outcome = shown, reserveBytes = FILLER_RESERVE_BYTES)
        }
        select(place.target.location)
    }

    /** Monotonic, so a wall-clock change during a long run cannot turn the estimate negative. */
    private fun nowMillis(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    private suspend fun select(location: String) {
        val version = ++selectionVersion
        val place = resolver.place(location)
        if (place == null) {
            if (version == selectionVersion) {
                mutableState.update {
                    it.copy(place = null, accessible = false, availableBytes = null, outcome = FillerOutcome.NOT_LOCAL)
                }
            }
            return
        }
        val accessible = storage.validate(place.target)
        val available = if (accessible) storage.availableBytes(place.target) else null
        if (version != selectionVersion) return
        mutableState.update {
            it.copy(
                place = place,
                accessible = accessible,
                availableBytes = available,
                outcome = it.outcome ?: selectionProblem(accessible, available, it.reserveBytes),
            )
        }
    }

    private suspend fun refreshPending() {
        val session = engine.pendingSessions().firstOrNull()
        pendingSession = session
        val described = session?.let { resolver.place(it.target.location) }
        mutableState.update { it.copy(pending = described ?: session?.let(::unnamedPlace)) }
    }

    private fun selectionProblem(accessible: Boolean, available: Long?, reserveBytes: Long): FillerOutcome? = when {
        !accessible -> FillerOutcome.NO_ACCESS
        available == null -> FillerOutcome.MEASUREMENT_UNAVAILABLE
        available <= reserveBytes -> FillerOutcome.INSUFFICIENT_SPACE
        else -> null
    }

    /** Only the no-space verdict depends on the reserve, so any other shown outcome is kept as it is. */
    private fun outcomeForReserve(current: FillerUiState, reserveBytes: Long): FillerOutcome? {
        val dependsOnReserve = current.outcome == null || current.outcome == FillerOutcome.INSUFFICIENT_SPACE
        return if (current.place != null && dependsOnReserve) {
            selectionProblem(current.accessible, current.availableBytes, reserveBytes)
        } else {
            current.outcome
        }
    }

    private fun outcomeOf(result: FillerResult): FillerOutcome = when (result) {
        FillerResult.Busy -> FillerOutcome.BUSY
        FillerResult.InvalidTarget -> FillerOutcome.NO_ACCESS
        FillerResult.MeasurementUnavailable -> FillerOutcome.MEASUREMENT_UNAVAILABLE
        FillerResult.InsufficientSpace -> FillerOutcome.INSUFFICIENT_SPACE
        FillerResult.PendingCleanup -> FillerOutcome.CLEANUP_INCOMPLETE
        is FillerResult.Finished ->
            if (result.cleanupComplete) FillerOutcome.DONE else FillerOutcome.CLEANUP_INCOMPLETE
        is FillerResult.Failed -> if (result.cleanupComplete) FillerOutcome.FAILED else FillerOutcome.CLEANUP_INCOMPLETE
    }

    /** A card that is not inserted cannot be named by the platform; its volume id is all that is left. */
    private fun unnamedPlace(session: FillerSession) =
        FillerPlace(session.target, session.target.volumeId, session.target.volumeId)

    private companion object {
        const val KEY_LOCATION = "filler_location"
    }
}
