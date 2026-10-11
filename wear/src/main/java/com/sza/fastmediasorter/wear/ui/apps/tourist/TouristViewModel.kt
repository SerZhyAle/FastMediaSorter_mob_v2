package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.UnitSystem
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import com.sza.fastmediasorter.wear.domain.tourist.WearWeatherPosition
import com.sza.fastmediasorter.wear.domain.usecase.ObserveWearTouristDashboardUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ObserveWearTouristWeatherUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PublishTouristHealthUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResetWearTouristStepsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResetWearTouristTripUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** S4150: at most one report to the phone per interval, and only when a value changed within it. */
private const val HEALTH_PUBLISH_INTERVAL_MS = 5_000L

private data class TouristHealthSample(
    val heartRateBpm: Int?,
    val bodyTemperatureCelsius: Float?,
)

/** Screen-local toggles, held as one flow so the telemetry combine stays inside the typed arity. */
private data class TouristLocalState(
    val isAthleteMode: Boolean = false,
    val isScreenLocked: Boolean = false,
)

/**
 * S3007: ViewModel managing live telemetry, active focus selection and reset actions on Wear OS.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TouristViewModel @Inject constructor(
    observeTelemetryUseCase: ObserveWearTouristDashboardUseCase,
    private val resetTripUseCase: ResetWearTouristTripUseCase,
    private val resetStepsUseCase: ResetWearTouristStepsUseCase,
    preferencesRepository: WearPreferencesRepository,
    private val publishTouristHealthUseCase: PublishTouristHealthUseCase,
    observeWeatherUseCase: ObserveWearTouristWeatherUseCase,
) : ViewModel() {

    private val selectedMetric = MutableStateFlow(TouristMetricType.SPEED)
    private val localState = MutableStateFlow(TouristLocalState())

    // The telemetry source registers its location listeners once, when it is subscribed. A permission
    // granted after that never reaches those listeners, so the grant re-subscribes instead of only
    // re-reading the flag.
    private val telemetryRestartKey = MutableStateFlow(0)

    // Written only while the screen collects the telemetry, so the publisher below never holds a sensor.
    private val healthSample = MutableStateFlow<TouristHealthSample?>(null)

    // S4152: the fix that drives the weather requests, written from the same collection as [healthSample].
    private val weatherPosition = MutableStateFlow<WearWeatherPosition?>(null)

    // Starts with null so the first telemetry frame is not held back until the stored answer is read.
    private val weather = observeWeatherUseCase(weatherPosition).onStart { emit(null) }

    init {
        viewModelScope.launch {
            healthSample
                .filterNotNull()
                .sample(HEALTH_PUBLISH_INTERVAL_MS)
                .collect { publishTouristHealthUseCase(it.heartRateBpm, it.bodyTemperatureCelsius) }
        }
    }

    val uiState: StateFlow<TouristUiState> = combine(
        combine(
            telemetryRestartKey.flatMapLatest { observeTelemetryUseCase() }
                .onEach(::recordHealthSample)
                .onEach(::recordWeatherPosition),
            weather,
        ) { telemetry, answer -> telemetry.copy(weather = answer) },
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

    private fun recordHealthSample(telemetry: WearTouristState) {
        if (telemetry.heartRateBpm == null && telemetry.bodyTemperatureCelsius == null) return
        healthSample.value = TouristHealthSample(telemetry.heartRateBpm, telemetry.bodyTemperatureCelsius)
    }

    private fun recordWeatherPosition(telemetry: WearTouristState) {
        val latitude = telemetry.latitude ?: return
        val longitude = telemetry.longitude ?: return
        weatherPosition.value = WearWeatherPosition(latitude, longitude)
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
