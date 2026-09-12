package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCategory
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry

private const val DEFAULT_SYSTOLIC = 120
private const val DEFAULT_DIASTOLIC = 80

/**
 * S3012: UI state for the blood pressure measurement and input screen.
 *
 * @param systolicInput current text value for systolic pressure.
 * @param diastolicInput current text value for diastolic pressure.
 * @param canSave whether the current inputs form a valid saveable measurement.
 * @param currentCategory live classification category for the currently entered inputs.
 * @param lastReading the most recent saved measurement from history.
 * @param lastReadingCategory classification category of the last saved measurement.
 * @param errorMessageRes validation error string resource, or null when valid.
 */
data class BloodPressureUiState(
    val systolicInput: String = DEFAULT_SYSTOLIC.toString(),
    val diastolicInput: String = DEFAULT_DIASTOLIC.toString(),
    val canSave: Boolean = true,
    val currentCategory: BloodPressureCategory? = BloodPressureCategory.NORMAL,
    val lastReading: BloodPressureHistoryEntry? = null,
    val lastReadingCategory: BloodPressureCategory? = null,
    @StringRes val errorMessageRes: Int? = null
)
