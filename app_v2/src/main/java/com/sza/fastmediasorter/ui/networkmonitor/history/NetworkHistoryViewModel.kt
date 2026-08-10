package com.sza.fastmediasorter.ui.networkmonitor.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ExportNetworkHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S1433: what came of the user asking for the history file. */
sealed interface HistoryExportOutcome {

    data class Ready(val uri: Uri) : HistoryExportOutcome

    /** The file could not be written or offered; the screen says so rather than doing nothing. */
    data object Unavailable : HistoryExportOutcome
}

/**
 * S1433: the stored measurements, plus the two things that can be done to them.
 *
 * The repository is injected rather than wrapped in an `Observe*` use case, unlike the live sources of this
 * ticket: `observeHistory()` is a Room flow, which is already main-safe and already domain-typed, so a use
 * case here would add a hop that sets nothing and maps nothing.
 */
@HiltViewModel
class NetworkHistoryViewModel @Inject constructor(
    private val historyRepository: NetworkMeasurementHistoryRepository,
    private val exportNetworkHistory: ExportNetworkHistoryUseCase,
) : ViewModel() {

    val measurements: StateFlow<List<NetworkMeasurement>> = historyRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _exportOutcome = MutableSharedFlow<HistoryExportOutcome>(extraBufferCapacity = 1)

    /** One-shot, with no replay: a rotation must not reopen the share sheet the user already dismissed. */
    val exportOutcome: SharedFlow<HistoryExportOutcome> = _exportOutcome.asSharedFlow()

    fun onClearConfirmed() {
        viewModelScope.launch { historyRepository.clear() }
    }

    fun onExportRequested() {
        viewModelScope.launch {
            val uri = exportNetworkHistory().getOrNull()
            _exportOutcome.emit(
                if (uri == null) HistoryExportOutcome.Unavailable else HistoryExportOutcome.Ready(uri)
            )
        }
    }
}
