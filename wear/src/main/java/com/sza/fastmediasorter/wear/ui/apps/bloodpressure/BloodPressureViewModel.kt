package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.bloodpressure.BloodPressureEstimate
import com.sza.fastmediasorter.wear.domain.bloodpressure.EstimateBloodPressureUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.ExtractPulseWaveFeaturesUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.ImportArchivedCalibrationsUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveAnalysis
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgCapture
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import com.sza.fastmediasorter.wear.domain.bodysensor.WearPpgDataSource
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCategory
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSource
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureCalibrationRepository
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * S3113: the blood-pressure screen estimates by itself, the way the heart-rate screen measures by itself.
 *
 * Opening it starts a pulse-wave window at once when the owner has enough calibration pairs; without them
 * it says how many are missing and never captures, because a window nothing can be fitted on would only
 * cost a sensor session. Every estimate lands in the history as an estimate.
 *
 * Feature extraction runs on the main dispatcher on purpose: one window is about 750 samples, which the
 * filter and beat detector cross in linear passes, and a dispatcher of its own would need a new DI
 * qualifier for no measurable gain.
 */
@HiltViewModel
class BloodPressureViewModel @Inject constructor(
    private val historyRepository: BloodPressureHistoryRepository,
    private val calibrationRepository: BloodPressureCalibrationRepository,
    private val ppgDataSource: WearPpgDataSource,
    private val extractFeatures: ExtractPulseWaveFeaturesUseCase,
    private val estimate: EstimateBloodPressureUseCase,
    private val importArchivedCalibrations: ImportArchivedCalibrationsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BloodPressureUiState())
    val state: StateFlow<BloodPressureUiState> = _state.asStateFlow()

    private var measurementJob: Job? = null

    init {
        viewModelScope.launch {
            historyRepository.observeAll().collect { entries ->
                _state.update { it.copy(lastReading = entries.firstOrNull()) }
            }
        }
        measurementJob = viewModelScope.launch {
            importArchivedCalibrations()
            measure()
        }
    }

    /** Starts a fresh window, abandoning a running one - its samples would belong to no estimate. */
    fun startMeasurement() {
        measurementJob?.cancel()
        measurementJob = viewModelScope.launch { measure() }
    }

    private suspend fun measure() {
        val pairs = calibrationRepository.getAll()
        if (pairs.size < EstimateBloodPressureUseCase.MIN_PAIRS) {
            publish(BloodPressureEstimatePhase.NotCalibrated(pairs.size, EstimateBloodPressureUseCase.MIN_PAIRS))
        } else {
            ppgDataSource.capture(WINDOW_MILLIS).collect { capture -> publish(phaseOf(capture, pairs)) }
        }
    }

    private suspend fun phaseOf(
        capture: PpgCapture,
        pairs: List<BloodPressureCalibration>
    ): BloodPressureEstimatePhase =
        when (capture) {
            is PpgCapture.Capturing -> BloodPressureEstimatePhase.Capturing(capture.elapsedMillis, capture.totalMillis)
            is PpgCapture.Unavailable -> BloodPressureEstimatePhase.Unavailable(capture.reason)
            is PpgCapture.Captured -> evaluate(capture.window, pairs)
        }

    private suspend fun evaluate(window: PpgWindow, pairs: List<BloodPressureCalibration>): BloodPressureEstimatePhase =
        when (val analysis = extractFeatures(window)) {
            is PulseWaveAnalysis.Rejected -> BloodPressureEstimatePhase.Rejected(analysis.reason)
            is PulseWaveAnalysis.Accepted -> when (val result = estimate(analysis.features, pairs)) {
                is BloodPressureEstimate.NotCalibrated ->
                    BloodPressureEstimatePhase.NotCalibrated(result.pairCount, result.requiredPairs)
                is BloodPressureEstimate.Estimated -> {
                    val pulse = analysis.features.heartRateBpm.roundToInt()
                    historyRepository.save(result.systolic, result.diastolic, BloodPressureSource.ESTIMATE, pulse)
                    estimatedPhaseOf(result, pulse)
                }
            }
        }

    private fun estimatedPhaseOf(result: BloodPressureEstimate.Estimated, pulse: Int) =
        BloodPressureEstimatePhase.Estimated(
            systolic = result.systolic,
            diastolic = result.diastolic,
            pulse = pulse,
            category = BloodPressureCategory.classify(result.systolic, result.diastolic),
            pairCount = result.pairCount,
            newestPairAgeDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - result.newestPairMillis),
            errorSystolic = result.leaveOneOutError?.systolicMmHg?.roundToInt(),
            errorDiastolic = result.leaveOneOutError?.diastolicMmHg?.roundToInt()
        )

    private fun publish(phase: BloodPressureEstimatePhase) {
        _state.update { it.copy(phase = phase, canMeasure = isRetryable(phase)) }
    }

    /**
     * "Measure again" is offered only where pressing it could end differently: not while a window runs,
     * not before calibration - the calibration chip is the way forward there - and not for a refusal the
     * user cannot change from this screen.
     */
    private fun isRetryable(phase: BloodPressureEstimatePhase): Boolean = when (phase) {
        is BloodPressureEstimatePhase.Capturing -> false
        is BloodPressureEstimatePhase.NotCalibrated -> false
        is BloodPressureEstimatePhase.Unavailable -> phase.reason !in PERMANENT_REFUSALS
        else -> true
    }

    companion object {
        const val WINDOW_MILLIS = 30_000L

        val PERMANENT_REFUSALS = setOf(
            BodySensorUnavailableReason.NOT_OFFERED_IN_THIS_BUILD,
            BodySensorUnavailableReason.PLATFORM_TOO_OLD,
            BodySensorUnavailableReason.NO_HARDWARE
        )
    }
}
