package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.UnitSystem
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.usecase.ObserveWearTouristDashboardUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResetWearTouristStepsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResetWearTouristTripUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Screen-local toggles, held as one flow so the telemetry combine stays inside the typed arity. */
private data class TouristLocalState(
    val isAthleteMode: Boolean = false,
    val isScreenLocked: Boolean = false,
)

/**
 * S3007: ViewModel managing live telemetry, active focus selection and reset actions on Wear OS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TouristViewModel @Inject constructor(
    observeTelemetryUseCase: ObserveWearTouristDashboardUseCase,
    private val resetTripUseCase: ResetWearTouristTripUseCase,
    private val resetStepsUseCase: ResetWearTouristStepsUseCase,
    preferencesRepository: WearPreferencesRepository,
) : ViewModel() {

    private val selectedMetric = MutableStateFlow(TouristMetricType.SPEED)
    private val localState = MutableStateFlow(TouristLocalState())

    // The telemetry source registers its location listeners once, when it is subscribed. A permission
    // granted after that never reaches those listeners, so the grant re-subscribes instead of only
    // re-reading the flag.
    private val telemetryRestartKey = MutableStateFlow(0)

    val uiState: StateFlow<TouristUiState> = combine(
        telemetryRestartKey.flatMapLatest { observeTelemetryUseCase() },
        selectedMetric,
        preferencesRepository.unitSystem,
        localState,
    ) { telemetry, metric, unitSystem, local ->
        TouristUiState(
            telemetry = telemetry.copy(focusedMetric = metric),
            isMetricSystem = unitSystem == UnitSystem.METRIC,
            isAthleteMode = local.isAthleteMode,
            isScreenLocked = local.isScreenLocked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = TouristUiState(),
    )

    fun selectMetric(metric: TouristMetricType) {
        selectedMetric.value = metric
    }

    fun toggleAthleteMode() {
        localState.value = localState.value.copy(isAthleteMode = !localState.value.isAthleteMode)
    }

    fun setScreenLocked(locked: Boolean) {
        localState.value = localState.value.copy(isScreenLocked = locked)
    }

    fun refreshPermissionState() {
        telemetryRestartKey.value += 1
    }

    fun resetTrip() {
        viewModelScope.launch {
            resetTripUseCase()
        }
    }

    fun resetSteps() {
        viewModelScope.launch {
            resetStepsUseCase()
        }
    }
}
