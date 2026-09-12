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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * S3007: ViewModel managing live telemetry, active focus selection and reset actions on Wear OS.
 */
@HiltViewModel
class TouristViewModel @Inject constructor(
    observeTelemetryUseCase: ObserveWearTouristDashboardUseCase,
    private val resetTripUseCase: ResetWearTouristTripUseCase,
    private val resetStepsUseCase: ResetWearTouristStepsUseCase,
    preferencesRepository: WearPreferencesRepository,
) : ViewModel() {

    private val selectedMetric = MutableStateFlow(TouristMetricType.SPEED)
    private val isAthleteMode = MutableStateFlow(false)
    private val isScreenLocked = MutableStateFlow(false)

    val uiState: StateFlow<TouristUiState> = combine(
        observeTelemetryUseCase(),
        selectedMetric,
        preferencesRepository.unitSystem,
        isAthleteMode,
        isScreenLocked,
    ) { telemetry, metric, unitSystem, athleteMode, screenLocked ->
        TouristUiState(
            telemetry = telemetry.copy(focusedMetric = metric),
            isMetricSystem = unitSystem == UnitSystem.METRIC,
            isAthleteMode = athleteMode,
            isScreenLocked = screenLocked,
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
        isAthleteMode.value = !isAthleteMode.value
    }

    fun setScreenLocked(locked: Boolean) {
        isScreenLocked.value = locked
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
