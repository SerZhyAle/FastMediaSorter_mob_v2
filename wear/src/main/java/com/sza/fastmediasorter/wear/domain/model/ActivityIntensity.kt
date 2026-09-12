package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sza.fastmediasorter.wear.R

private const val COLOR_SEDENTARY = 0xFF78909C
private const val COLOR_LIGHT = 0xFF4CAF50
private const val COLOR_ACTIVE = 0xFFFF9800
private const val COLOR_HIGH = 0xFFF44336

private const val SEDENTARY_MAX_STEPS = 3000
private const val LIGHT_MAX_STEPS = 6000
private const val ACTIVE_MAX_STEPS = 10000

/**
 * S3014: Daily/session physical activity intensity classification based on step count.
 *
 * - SEDENTARY: < 3 000 steps (Low / Resting activity)
 * - LIGHT: 3 000 - 5 999 steps (Light activity / Daily baseline)
 * - ACTIVE: 6 000 - 9 999 steps (Active / Recommended target)
 * - HIGH: >= 10 000 steps (High activity / Athletic goal)
 */
enum class ActivityIntensity(
    @StringRes val labelRes: Int,
    val color: Color
) {
    SEDENTARY(
        labelRes = R.string.activity_intensity_sedentary,
        color = Color(COLOR_SEDENTARY)
    ),
    LIGHT(
        labelRes = R.string.activity_intensity_light,
        color = Color(COLOR_LIGHT)
    ),
    ACTIVE(
        labelRes = R.string.activity_intensity_active,
        color = Color(COLOR_ACTIVE)
    ),
    HIGH(
        labelRes = R.string.activity_intensity_high,
        color = Color(COLOR_HIGH)
    );

    companion object {
        /**
         * Classifies a step count into an activity intensity tier.
         */
        fun classify(steps: Long): ActivityIntensity {
            return when {
                steps < SEDENTARY_MAX_STEPS -> SEDENTARY
                steps < LIGHT_MAX_STEPS -> LIGHT
                steps < ACTIVE_MAX_STEPS -> ACTIVE
                else -> HIGH
            }
        }
    }
}
