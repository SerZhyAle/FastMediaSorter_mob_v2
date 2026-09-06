package com.sza.fastmediasorter.ui.stopwatch

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.util.ElapsedClock
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchSettings
import com.sza.fastmediasorter.domain.usecase.stopwatch.ObserveStopwatchSettingsUseCase
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the measurement for every participant.
 *
 * The screen survives rotation because the state lives here and elapsed time is a function of the
 * stored marks and a fresh clock reading. [now] exists only to repaint - it is never the source of an
 * elapsed value, so a dropped or delayed tick costs a frame and never a millisecond of the measurement
 * (S1411 §6.6, ADR-8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StopwatchViewModel @Inject constructor(
    private val clock: ElapsedClock,
    private val observeStopwatchSettings: ObserveStopwatchSettingsUseCase,
    mediaCapabilities: MediaCapabilities,
) : ViewModel() {

    private val _state = MutableStateFlow(StopwatchScreenState())
    val state: StateFlow<StopwatchScreenState> = _state.asStateFlow()

    /**
     * Whether this build can play the accompaniment at all. `photos` declares no audio, and Rule 14
     * forbids a flavor guard in shared code - the typed capability record is the sanctioned seam.
     */
    val audioAvailable: Boolean = mediaCapabilities.supportsAudio

    private val _trackUri = MutableStateFlow<Uri?>(null)

    /** The accompaniment's track, resolved from the tool's own settings and the build's capability. */
    val trackUri: StateFlow<Uri?> = _trackUri.asStateFlow()

    private val _volumeKeysDriveMeasurement = MutableStateFlow(true)

    /** ADR-2's escape hatch, now a stored setting rather than the phase 04 default. */
    val volumeKeysDriveMeasurement: StateFlow<Boolean> = _volumeKeysDriveMeasurement.asStateFlow()

    init {
        // The tool's options are edited from two surfaces (§6.4), so the screen follows the store
        // rather than reading it once: a change made in Settings reaches an open stopwatch.
        viewModelScope.launch {
            observeStopwatchSettings().collect { settings -> applySettings(settings) }
        }
    }

    private fun applySettings(settings: StopwatchSettings) {
        setParticipantCount(settings.participantCount)
        _volumeKeysDriveMeasurement.value = settings.volumeKeysDriveMeasurement
        val wanted = settings.musicUri
        _trackUri.value = if (audioAvailable && settings.musicEnabled && wanted.isNotEmpty()) {
            wanted.toUri()
        } else {
            null
        }
    }

    /**
     * Repaint clock, cold on purpose. It emits once for a still screen and then ticks only while a
     * collector is attached and something is running, so a screen the user left stops repainting
     * instead of waking thirty times a second to redraw views nobody is looking at. Owning the ticker
     * in the ViewModel's own scope would keep it awake for the life of the screen.
     */
    val now: Flow<Long> = state
        .map { it.anyRunning }
        .distinctUntilChanged()
        .flatMapLatest { running ->
            flow {
                emit(clock.nowMillis())
                while (running) {
                    delay(REPAINT_INTERVAL_MILLIS)
                    emit(clock.nowMillis())
                }
            }
        }

    /** The one command every input source maps onto: run it, or record a split if it already runs. */
    fun startOrLap(participantId: Int) {
        val nowMillis = clock.nowMillis()
        _state.value = if (_state.value.participants.getOrNull(participantId)?.running == true) {
            StopwatchEngine.lap(_state.value, participantId, nowMillis)
        } else {
            StopwatchEngine.start(_state.value, participantId, nowMillis)
        }
    }

    fun stop(participantId: Int) {
        _state.value = StopwatchEngine.stop(_state.value, participantId, clock.nowMillis())
    }

    fun reset(participantId: Int) {
        _state.value = StopwatchEngine.reset(_state.value, participantId)
    }

    fun resetAll() {
        _state.value = StopwatchEngine.resetAll(_state.value)
    }

    fun setParticipantCount(count: Int) {
        _state.value = StopwatchEngine.withParticipantCount(_state.value, count)
    }

    /** Current elapsed value for one participant, for a caller that needs it outside a repaint. */
    fun elapsedOf(participantId: Int): Long =
        _state.value.participants.getOrNull(participantId)?.elapsedAt(clock.nowMillis()) ?: 0L

    /**
     * One clock reading, for a caller that renders every participant at once outside a repaint.
     *
     * Taking it here rather than letting the caller reach for its own clock keeps the export on the same
     * monotonic source as the screen (ADR-8), and taking it once means the four totals in an exported
     * result belong to a single instant instead of drifting apart as the render walks the list.
     */
    fun nowMillis(): Long = clock.nowMillis()

    private companion object {
        /** About thirty repaints a second - enough for a hundredths reading not to look stepped. */
        const val REPAINT_INTERVAL_MILLIS = 33L
    }
}
