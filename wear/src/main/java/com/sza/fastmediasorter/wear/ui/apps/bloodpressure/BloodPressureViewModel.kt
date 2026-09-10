package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
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

/**
 * S2809: holds the blood pressure input screen state and saves measurements to the repository.
 *
 * The ViewModel owns validation (range 40-300, systolic >= diastolic) and the save action, so the
 * screen is free of business logic. The `lastReading` is kept current by collecting the
 * repository flow in `init`.
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
                _state.value = _state.value.copy(lastReading = entries.firstOrNull())
            }
        }
    }

    fun onSystolicChanged(value: String) {
        _state.value = _state.value.copy(systolicInput = value.filter { it.isDigit() })
        updateCanSave()
    }

    fun onDiastolicChanged(value: String) {
        _state.value = _state.value.copy(diastolicInput = value.filter { it.isDigit() })
        updateCanSave()
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
            Timber.d("S2809: saved blood pressure $systolic/$diastolic mmHg")
            _state.value = BloodPressureUiState()
        }
    }

    private fun updateCanSave() {
        val systolic = _state.value.systolicInput.toIntOrNull()
        val diastolic = _state.value.diastolicInput.toIntOrNull()
        _state.value = _state.value.copy(
            canSave = systolic != null && diastolic != null && isValid(systolic, diastolic),
            errorMessageRes = null
        )
    }

    private fun isValid(systolic: Int, diastolic: Int): Boolean =
        systolic in MIN_MMHG..MAX_MMHG &&
            diastolic in MIN_MMHG..MAX_MMHG &&
            systolic >= diastolic
}
