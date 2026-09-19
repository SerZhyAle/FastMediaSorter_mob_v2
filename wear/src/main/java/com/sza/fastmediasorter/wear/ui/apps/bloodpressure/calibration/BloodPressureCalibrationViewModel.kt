package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.bloodpressure.ExtractPulseWaveFeaturesUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveAnalysis
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgCapture
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import com.sza.fastmediasorter.wear.domain.bodysensor.WearPpgDataSource
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureCalibrationRepository
import com.sza.fastmediasorter.wear.domain.repository.PpgWindowRepository
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.BloodPressureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_MMHG = 40
private const val MAX_MMHG = 300
private const val MAX_INPUT_DIGITS = 3

/**
 * S3113: the calibration screen - a cuff reading typed over a pulse-wave window captured at the same time.
 *
 * An accepted window is archived the moment it completes, not on "Save": the typed values arrive through
 * the watch keyboard after the window has ended, and research 03 records a cuff reading lost when the
 * screen closed between the two. "Save" is offered only once the window is accepted, so a pair can never
 * be stored against a window the estimate would have refused.
 */
@HiltViewModel
class BloodPressureCalibrationViewModel @Inject constructor(
    private val calibrationRepository: BloodPressureCalibrationRepository,
    private val ppgDataSource: WearPpgDataSource,
    private val ppgWindowRepository: PpgWindowRepository,
    private val extractFeatures: ExtractPulseWaveFeaturesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BloodPressureCalibrationUiState())
    val state: StateFlow<BloodPressureCalibrationUiState> = _state.asStateFlow()

    private var recordingJob: Job? = null

    init {
        viewModelScope.launch {
            calibrationRepository.observeAll().collect { pairs -> _state.update { it.copy(pairs = pairs) } }
        }
        startRecording()
    }

    /** Starts a fresh window, abandoning a running one - its samples would belong to no cuff reading. */
    fun startRecording() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            ppgDataSource.capture(BloodPressureViewModel.WINDOW_MILLIS).collect { capture ->
                val window = windowStateOf(capture)
                _state.update { it.copy(window = window, justSaved = false).derived() }
            }
        }
    }

    fun onSystolicChanged(value: String) {
        _state.update { it.copy(systolicInput = digitsOf(value)).derived() }
    }

    fun onDiastolicChanged(value: String) {
        _state.update { it.copy(diastolicInput = digitsOf(value)).derived() }
    }

    fun adjustSystolic(delta: Int) {
        _state.update { it.copy(systolicInput = adjusted(it.systolicInput, delta)).derived() }
    }

    fun adjustDiastolic(delta: Int) {
        _state.update { it.copy(diastolicInput = adjusted(it.diastolicInput, delta)).derived() }
    }

    fun save() {
        val current = _state.value
        val ready = current.window as? CalibrationWindowState.Ready
        if (!current.canSave || ready == null) return
        viewModelScope.launch {
            calibrationRepository.save(
                systolic = current.systolicInput.toInt(),
                diastolic = current.diastolicInput.toInt(),
                features = ready.features,
                windowFile = ready.fileName
            )
            _state.update { it.copy(window = null, justSaved = true).derived() }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { calibrationRepository.delete(id) }
    }

    private suspend fun windowStateOf(capture: PpgCapture): CalibrationWindowState = when (capture) {
        is PpgCapture.Capturing -> CalibrationWindowState.Capturing(capture.elapsedMillis, capture.totalMillis)
        is PpgCapture.Unavailable -> CalibrationWindowState.Unavailable(capture.reason)
        is PpgCapture.Captured -> judged(capture.window)
    }

    private suspend fun judged(window: PpgWindow): CalibrationWindowState =
        when (val analysis = extractFeatures(window)) {
            is PulseWaveAnalysis.Rejected -> CalibrationWindowState.Rejected(analysis.reason)
            is PulseWaveAnalysis.Accepted -> CalibrationWindowState.Ready(
                fileName = ppgWindowRepository.save(window, label = null),
                features = analysis.features
            )
        }

    private fun BloodPressureCalibrationUiState.derived(): BloodPressureCalibrationUiState {
        val systolic = systolicInput.toIntOrNull()
        val diastolic = diastolicInput.toIntOrNull()
        val valid = systolic != null && diastolic != null && isValid(systolic, diastolic)
        val complete = systolic != null && diastolic != null
        return copy(
            errorMessageRes = if (valid || !complete) null else R.string.blood_pressure_invalid,
            canSave = valid && window is CalibrationWindowState.Ready,
            canRecord = isRestartable(window)
        )
    }

    private fun isRestartable(window: CalibrationWindowState?): Boolean = when (window) {
        is CalibrationWindowState.Capturing -> false
        is CalibrationWindowState.Unavailable -> window.reason !in BloodPressureViewModel.PERMANENT_REFUSALS
        else -> true
    }

    private fun digitsOf(value: String): String = value.filter { it.isDigit() }.take(MAX_INPUT_DIGITS)

    private fun adjusted(input: String, delta: Int): String =
        ((input.toIntOrNull() ?: MIN_MMHG) + delta).coerceIn(MIN_MMHG, MAX_MMHG).toString()

    private fun isValid(systolic: Int, diastolic: Int): Boolean =
        systolic in MIN_MMHG..MAX_MMHG && diastolic in MIN_MMHG..MAX_MMHG && systolic >= diastolic
}
