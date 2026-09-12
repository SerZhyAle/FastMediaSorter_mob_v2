package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sza.fastmediasorter.wear.R

private const val COLOR_LOW = 0xFF42A5F5
private const val COLOR_NORMAL = 0xFF4CAF50
private const val COLOR_ELEVATED = 0xFFFFEB3B
private const val COLOR_STAGE_1 = 0xFFFF9800
private const val COLOR_STAGE_2 = 0xFFF44336
private const val COLOR_CRISIS = 0xFFB71C1C

/**
 * S3012: Blood pressure classification according to standard WHO and AHA/ACC guidelines.
 *
 * Blood pressure is classified by the higher category of systolic or diastolic values:
 * - LOW: < 90 SYS or < 60 DIA
 * - NORMAL: < 120 SYS and < 80 DIA
 * - ELEVATED: 120-129 SYS and < 80 DIA
 * - HYPERTENSION_STAGE_1: 130-139 SYS or 80-89 DIA
 * - HYPERTENSION_STAGE_2: 140-179 SYS or 90-119 DIA
 * - HYPERTENSIVE_CRISIS: >= 180 SYS or >= 120 DIA
 */
enum class BloodPressureCategory(
    @StringRes val labelRes: Int,
    val color: Color
) {
    LOW(
        labelRes = R.string.blood_pressure_cat_low,
        color = Color(COLOR_LOW)
    ),
    NORMAL(
        labelRes = R.string.blood_pressure_cat_normal,
        color = Color(COLOR_NORMAL)
    ),
    ELEVATED(
        labelRes = R.string.blood_pressure_cat_elevated,
        color = Color(COLOR_ELEVATED)
    ),
    HYPERTENSION_STAGE_1(
        labelRes = R.string.blood_pressure_cat_stage1,
        color = Color(COLOR_STAGE_1)
    ),
    HYPERTENSION_STAGE_2(
        labelRes = R.string.blood_pressure_cat_stage2,
        color = Color(COLOR_STAGE_2)
    ),
    HYPERTENSIVE_CRISIS(
        labelRes = R.string.blood_pressure_cat_crisis,
        color = Color(COLOR_CRISIS)
    );

    companion object {
        private const val LOW_SYS = 90
        private const val LOW_DIA = 60
        private const val NORMAL_SYS = 120
        private const val NORMAL_DIA = 80
        private const val ELEVATED_SYS = 130
        private const val STAGE1_SYS = 140
        private const val STAGE1_DIA = 90
        private const val CRISIS_SYS = 180
        private const val CRISIS_DIA = 120

        /**
         * Classifies systolic and diastolic readings according to standard guidelines.
         */
        fun classify(systolic: Int, diastolic: Int): BloodPressureCategory {
            return when {
                systolic >= CRISIS_SYS || diastolic >= CRISIS_DIA -> HYPERTENSIVE_CRISIS
                systolic >= STAGE1_SYS || diastolic >= STAGE1_DIA -> HYPERTENSION_STAGE_2
                systolic >= ELEVATED_SYS || diastolic in NORMAL_DIA until STAGE1_DIA -> HYPERTENSION_STAGE_1
                systolic in NORMAL_SYS until ELEVATED_SYS && diastolic < NORMAL_DIA -> ELEVATED
                systolic < LOW_SYS || diastolic < LOW_DIA -> LOW
                else -> NORMAL
            }
        }
    }
}
