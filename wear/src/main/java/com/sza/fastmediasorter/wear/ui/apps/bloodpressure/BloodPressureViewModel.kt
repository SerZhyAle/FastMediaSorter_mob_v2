package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCategory
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val MIN_MMHG = 40
private const val MAX_MMHG = 300
private const val DEFAULT_SYSTOLIC = 120
private const val DEFAULT_DIASTOLIC = 80
private const val MAX_INPUT_DIGITS = 3

/**
 * S3012: holds the blood pressure input screen state and saves measurements to the repository.
 *
 * The ViewModel owns validation (range 40-300, systolic >= diastolic), stepper adjustments (+/-),
 * live category classification, and the save action.
 */
@HiltViewModel
class BloodPressureViewModel @Inject constructor(
    private val repository: BloodPressureHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BloodPressureUiState())
    val state: StateFlow<BloodPressureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { entries ->
                val last = entries.firstOrNull()
                val lastCat = last?.let { BloodPressureCategory.classify(it.systolic, it.diastolic) }
                _state.value = _state.value.copy(
                    lastReading = last,
                    lastReadingCategory = lastCat
                )
            }
        }
    }

    fun onSystolicChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(MAX_INPUT_DIGITS)
        _state.value = _state.value.copy(systolicInput = filtered)
        updateCalculatedState()
    }

    fun onDiastolicChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(MAX_INPUT_DIGITS)
        _state.value = _state.value.copy(diastolicInput = filtered)
        updateCalculatedState()
    }

    fun adjustSystolic(delta: Int) {
        val current = _state.value.systolicInput.toIntOrNull() ?: DEFAULT_SYSTOLIC
        val updated = (current + delta).coerceIn(MIN_MMHG, MAX_MMHG)
        _state.value = _state.value.copy(systolicInput = updated.toString())
        updateCalculatedState()
    }

    fun adjustDiastolic(delta: Int) {
        val current = _state.value.diastolicInput.toIntOrNull() ?: DEFAULT_DIASTOLIC
        val updated = (current + delta).coerceIn(MIN_MMHG, MAX_MMHG)
        _state.value = _state.value.copy(diastolicInput = updated.toString())
        updateCalculatedState()
    }

    fun save() {
        val current = _state.value
        val systolic = current.systolicInput.toIntOrNull()
        val diastolic = current.diastolicInput.toIntOrNull()
        if (systolic == null || diastolic == null) return
        if (!isValid(systolic, diastolic)) {
            _state.value = current.copy(errorMessageRes = R.string.blood_pressure_invalid)
            return
        }
        viewModelScope.launch {
            repository.save(systolic, diastolic)
            Timber.d("S3012: saved blood pressure $systolic/$diastolic mmHg")
            val lastCat = BloodPressureCategory.classify(systolic, diastolic)
            _state.value = _state.value.copy(
                errorMessageRes = null,
                lastReadingCategory = lastCat
            )
        }
    }

    private fun updateCalculatedState() {
        val systolic = _state.value.systolicInput.toIntOrNull()
        val diastolic = _state.value.diastolicInput.toIntOrNull()
        val valid = systolic != null && diastolic != null && isValid(systolic, diastolic)
        val category = if (systolic != null && diastolic != null && isValid(systolic, diastolic)) {
            BloodPressureCategory.classify(systolic, diastolic)
        } else {
            null
        }
        val errorRes = if (valid || systolic == null || diastolic == null) null else R.string.blood_pressure_invalid
        _state.value = _state.value.copy(
            canSave = valid,
            currentCategory = category,
            errorMessageRes = errorRes
        )
    }

    private fun isValid(systolic: Int, diastolic: Int): Boolean =
        systolic in MIN_MMHG..MAX_MMHG &&
            diastolic in MIN_MMHG..MAX_MMHG &&
            systolic >= diastolic
}
