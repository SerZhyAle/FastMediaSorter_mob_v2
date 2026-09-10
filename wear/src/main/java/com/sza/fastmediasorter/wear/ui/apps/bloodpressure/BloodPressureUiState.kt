package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry

/**
 * S2809: everything the blood pressure input screen shows at one moment.
 *
 * @param systolicInput the current text in the systolic field.
 * @param diastolicInput the current text in the diastolic field.
 * @param canSave whether the save chip is offered. Derived in the ViewModel so the screen holds
 *   no second derivation that could disagree.
 * @param lastReading the most recent saved measurement, shown as a hint above the input fields.
 * @param errorMessageRes a validation error string resource, or null when the inputs are valid.
 */
data class BloodPressureUiState(
    val systolicInput: String = "",
    val diastolicInput: String = "",
    val canSave: Boolean = false,
    val lastReading: BloodPressureHistoryEntry? = null,
    @StringRes val errorMessageRes: Int? = null
)
