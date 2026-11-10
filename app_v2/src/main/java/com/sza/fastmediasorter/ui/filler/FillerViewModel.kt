package com.sza.fastmediasorter.ui.filler

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.filler.FILLER_RESERVE_BYTES
import com.sza.fastmediasorter.domain.filler.FillerEngine
import com.sza.fastmediasorter.domain.filler.FillerPlace
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
    val pending: FillerPlace? = null,
    val outcome: FillerOutcome? = null,
    val reserveBytes: Long = FILLER_RESERVE_BYTES,
) {
    val canStart: Boolean
        get() = !running && pending == null && accessible && (availableBytes ?: 0) > reserveBytes
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

    private var run: Job? = null
    private var pendingSession: FillerSession? = null

    init {
        viewModelScope.launch {
            refreshPending()
            savedState.get<String>(KEY_LOCATION)?.let { select(it) }
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

    fun start() {
        val current = state.value
        val place = current.place
        if (!current.canStart || place == null || run?.isActive == true) return
        Timber.d("S4521: filler start requested")
        val planned = (current.availableBytes ?: 0) - current.reserveBytes
        mutableState.update { it.copy(running = true, plannedBytes = planned, writtenBytes = 0, outcome = null) }
        run = viewModelScope.launch {
            var outcome = FillerOutcome.CANCELLED
            try {
                outcome = outcomeOf(
                    engine.run(place.target, current.reserveBytes) { written ->
                        mutableState.update { it.copy(writtenBytes = written) }
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
        Timber.d("S4521: filler cleanup retry requested")
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
        mutableState.update { it.copy(running = false, outcome = shown) }
        select(place.target.location)
    }

    private suspend fun select(location: String) {
        val place = resolver.place(location)
        if (place == null) {
            mutableState.update {
                it.copy(place = null, accessible = false, availableBytes = null, outcome = FillerOutcome.NOT_LOCAL)
            }
            return
        }
        val accessible = storage.validate(place.target)
        val available = if (accessible) storage.availableBytes(place.target) else null
        mutableState.update {
            it.copy(
                place = place,
                accessible = accessible,
                availableBytes = available,
                outcome = it.outcome ?: selectionProblem(accessible, available),
            )
        }
    }

    private suspend fun refreshPending() {
        val session = engine.pendingSessions().firstOrNull()
        pendingSession = session
        val described = session?.let { resolver.place(it.target.location) }
        mutableState.update { it.copy(pending = described ?: session?.let(::unnamedPlace)) }
    }

    private fun selectionProblem(accessible: Boolean, available: Long?): FillerOutcome? = when {
        !accessible -> FillerOutcome.NO_ACCESS
        available == null -> FillerOutcome.MEASUREMENT_UNAVAILABLE
        available <= FILLER_RESERVE_BYTES -> FillerOutcome.INSUFFICIENT_SPACE
        else -> null
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
