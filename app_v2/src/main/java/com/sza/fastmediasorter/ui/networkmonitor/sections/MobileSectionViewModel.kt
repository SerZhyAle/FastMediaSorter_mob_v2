package com.sza.fastmediasorter.ui.networkmonitor.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSeries
import com.sza.fastmediasorter.domain.model.networkmonitor.SimEntry
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveCellularSignalUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.SimSignalSample
import com.sza.fastmediasorter.ui.networkmonitor.helpers.ChartWindowEvent
import com.sza.fastmediasorter.ui.networkmonitor.helpers.ChartWindowResetManager
import com.sza.fastmediasorter.ui.networkmonitor.helpers.withChartResets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** S1433: the accumulated chart window of one SIM, addressed by the slot the section labels it with. */
data class SimSignalWindow(
    val slotIndex: Int,
    val series: SignalSeries,
)

/**
 * S1433: everything the Mobile subscreen renders.
 *
 * [activeModemCount] sits outside [sims] because the snapshot keeps it there: the count is readable with no
 * permission at all, so it is shown unconditionally, while the per-SIM detail behind [sims] appears only
 * after `READ_PHONE_STATE`.
 */
data class MobileSectionUiState(
    val activeModemCount: Int?,
    val sims: MonitorSection<List<SimEntry>>,
    val signals: MonitorSection<List<SimSignalWindow>>,
) {
    companion object {

        /** Shown for the frame between the screen opening and the first snapshot arriving. */
        val Empty = MobileSectionUiState(
            activeModemCount = null,
            sims = MonitorSection.absent(SectionAvailability.NoNetwork),
            signals = MonitorSection.absent(SectionAvailability.NoNetwork),
        )
    }
}

/**
 * S1433: composes the Mobile subscreen.
 *
 * No radio contract is injected here, and that is the point of the section rather than an omission. Mobile
 * data needs `MODIFY_PHONE_STATE` and `SubscriptionManager` exposes no enable path at all (strategic §3.2),
 * so a switch on this screen could never work - the section offers the system surface instead and says so.
 */
@HiltViewModel
class MobileSectionViewModel @Inject constructor(
    repository: NetworkMonitorRepository,
    observeCellularSignal: ObserveCellularSignalUseCase,
) : ViewModel() {

    private val chartResets = ChartWindowResetManager()

    val uiState: StateFlow<MobileSectionUiState> = combine(
        repository.observeSnapshot(),
        observeCellularSignal()
            .withChartResets(chartResets.resets)
            .scan(emptySimWindows()) { windows, event ->
                when (event) {
                    is ChartWindowEvent.Sample -> windows.append(event.reading)
                    is ChartWindowEvent.Reset -> windows.clear(event.chartKey)
                }
            },
    ) { snapshot, signals -> snapshot.toUiState(signals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MobileSectionUiState.Empty)

    /** Starts the window of the chart drawn for [slotIndex] over, leaving the other SIM's history alone. */
    fun onChartResetRequested(slotIndex: Int) {
        chartResets.reset(ChartWindowResetManager.simChart(slotIndex))
    }
}

private fun emptySimWindows(): MonitorSection<List<SimSignalWindow>> =
    MonitorSection.absent(SectionAvailability.NoNetwork)

/**
 * Folds a reading into the per-SIM windows, keeping each SIM's own history.
 *
 * A SIM that stops reporting drops out of the emission and therefore out of the result: the sampler omits a
 * series rather than zero-filling it, and carrying a stale window forward would redraw a signal that is no
 * longer being measured.
 */
private fun MonitorSection<List<SimSignalWindow>>.append(
    reading: MonitorSection<List<SimSignalSample>>,
): MonitorSection<List<SimSignalWindow>> {
    val samples = reading.data ?: return MonitorSection.absent(reading.availability)
    val existing = data.orEmpty().associateBy { it.slotIndex }
    return MonitorSection.available(
        samples.map { sample ->
            val series = existing[sample.slotIndex]?.series ?: SignalSeries()
            SimSignalWindow(sample.slotIndex, series.append(sample.sample))
        }
    )
}

/**
 * Empties the window of one SIM's chart, keeping every other SIM's history.
 *
 * The section draws one chart per SIM from a single flow, so a reset that cleared the whole map would
 * restart a chart the user did not tap (S1853).
 */
private fun MonitorSection<List<SimSignalWindow>>.clear(
    chartKey: String,
): MonitorSection<List<SimSignalWindow>> {
    val windows = data ?: return this
    return MonitorSection.available(
        windows.map { window ->
            if (ChartWindowResetManager.simChart(window.slotIndex) == chartKey) {
                SimSignalWindow(window.slotIndex, SignalSeries())
            } else {
                window
            }
        }
    )
}

private fun NetworkMonitorSnapshot.toUiState(
    signals: MonitorSection<List<SimSignalWindow>>,
): MobileSectionUiState = MobileSectionUiState(
    activeModemCount = activeModemCount,
    sims = sims,
    signals = signals,
)
