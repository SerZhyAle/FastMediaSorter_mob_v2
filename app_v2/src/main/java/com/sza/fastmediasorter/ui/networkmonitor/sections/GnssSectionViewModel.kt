package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssTrackState
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSample
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSeries
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveGnssStatusUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.RecordGnssTrackUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ShareGnssTrackUseCase
import com.sza.fastmediasorter.ui.networkmonitor.helpers.appendSample
import com.sza.fastmediasorter.ui.networkmonitor.helpers.emptySignalWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S1433: what came of the user asking for the recorded track. */
sealed interface GnssShareOutcome {

    /** The file is ready and the screen may hand [uri] to the system share sheet. */
    data class Ready(val uri: Uri) : GnssShareOutcome

    /** The session file is gone or could not be offered; the screen says so instead of failing silently. */
    data object Unavailable : GnssShareOutcome
}

/**
 * S1433: everything the GNSS subscreen renders, already decided.
 *
 * [isAcquiring] separates "the receiver is warming up" from "the sky is empty", which Phase 06's audit left
 * as an open handoff: the first live emission is an available snapshot with no satellites, before any
 * callback has fired. No timer is used to decide it, because any window would be an invented number - an
 * available receiver reporting nothing in view has simply not answered yet, and the moment it does the flag
 * clears itself.
 */
data class GnssSectionUiState(
    val gnss: MonitorSection<GnssSnapshot>,
    val signal: MonitorSection<SignalSeries>,
) {

    val isAcquiring: Boolean
        get() = gnss.availability is SectionAvailability.Available && gnss.data?.satellites.isNullOrEmpty()

    companion object {

        /** Shown for the frame between the screen opening and the first snapshot arriving. */
        val Empty = GnssSectionUiState(
            gnss = MonitorSection.absent(SectionAvailability.NoNetwork),
            signal = emptySignalWindow(),
        )
    }
}

/**
 * S1433: composes the GNSS subscreen and owns the track-recording opt-in.
 *
 * The status flow is shared rather than collected twice. Both the screen and the recorder need it, and a
 * second collection would register a second `GnssStatus.Callback` and a second location request for one open
 * screen - twice the receiver work for the same picture. `WhileSubscribed()` with no stop timeout keeps the
 * "no background work" invariant: the collector is the Fragment's STARTED lifecycle, so the callbacks are
 * dropped the moment the screen is.
 */
@HiltViewModel
class GnssSectionViewModel @Inject constructor(
    observeGnssStatus: ObserveGnssStatusUseCase,
    recordGnssTrack: RecordGnssTrackUseCase,
    private val shareGnssTrack: ShareGnssTrackUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val status: SharedFlow<MonitorSection<GnssSnapshot>> = observeGnssStatus()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val uiState: StateFlow<GnssSectionUiState> = status
        .scan(GnssSectionUiState.Empty) { previous, section -> previous.fold(section) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), GnssSectionUiState.Empty)

    /**
     * `distinctUntilChanged` because a satellite update re-emits the position the receiver already reported:
     * without it a stationary device would fill the track with copies of one point.
     */
    val track: StateFlow<GnssTrackState> = recordGnssTrack(
        status.mapNotNull { section -> section.data?.coordinate }.distinctUntilChanged()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), GnssTrackState.IDLE)

    /** The opt-in itself, read live so the switch shows what is stored rather than what was last tapped. */
    val recordTrack: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { settings -> settings.recordGnssTrack }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _shareOutcome = MutableSharedFlow<GnssShareOutcome>(extraBufferCapacity = 1)

    /**
     * One-shot, with no replay.
     *
     * A share is acted on once; replaying the last one would reopen the system share sheet on every rotation,
     * long after the tap that asked for it.
     */
    val shareOutcome: SharedFlow<GnssShareOutcome> = _shareOutcome.asSharedFlow()

    fun onRecordTrackChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { settings -> settings.copy(recordGnssTrack = enabled) }
        }
    }

    fun onShareTrackRequested() {
        val path = track.value.trackFilePath ?: return
        viewModelScope.launch {
            val uri = shareGnssTrack(path).getOrNull()
            _shareOutcome.emit(
                if (uri == null) GnssShareOutcome.Unavailable else GnssShareOutcome.Ready(uri)
            )
        }
    }
}

/**
 * Folds one snapshot into the state, including the chart window.
 *
 * The charted value is the mean C/N0 across the satellites that reported one. A per-satellite chart would
 * need a line per satellite and would redraw its whole legend every time one rose or set; the mean answers
 * the question the section exists for - is reception getting better or worse - with one line.
 */
private fun GnssSectionUiState.fold(section: MonitorSection<GnssSnapshot>): GnssSectionUiState {
    val snapshot = section.data
    val meanCn0 = snapshot?.meanCn0DbHz
    val reading = if (snapshot == null || meanCn0 == null) {
        MonitorSection.absent<SignalSample>(section.availability)
    } else {
        MonitorSection.available(SignalSample(snapshot.takenAtMillis, meanCn0))
    }
    return GnssSectionUiState(gnss = section, signal = signal.appendSample(reading))
}
