package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sza.fastmediasorter.wear.R

private const val COLOR_LOW = 0xFF42A5F5
private const val COLOR_NORMAL = 0xFF4CAF50
private const val COLOR_ELEVATED = 0xFFFFEB3B
private const val COLOR_CARDIO = 0xFFFF9800
private const val COLOR_PEAK = 0xFFF44336
private const val COLOR_EXTREME = 0xFFB71C1C

private const val LOW_MAX_BPM = 60
private const val NORMAL_MAX_BPM = 100
private const val ELEVATED_MAX_BPM = 120
private const val CARDIO_MAX_BPM = 140
private const val PEAK_MAX_BPM = 170

/**
 * S3013: Heart rate zones based on standard physiological guidelines.
 *
 * - LOW: < 60 BPM (Bradycardia / Resting Low)
 * - NORMAL: 60-100 BPM (Normal Resting Heart Rate)
 * - ELEVATED: 101-120 BPM (Warm-up / Light Activity)
 * - CARDIO: 121-140 BPM (Aerobic / Fat Burn)
 * - PEAK: 141-170 BPM (Anaerobic / High Intensity)
 * - EXTREME: > 170 BPM (Max Effort)
 */
enum class HeartRateZone(
    @StringRes val labelRes: Int,
    val color: Color
) {
    LOW(
        labelRes = R.string.heart_rate_zone_low,
        color = Color(COLOR_LOW)
    ),
    NORMAL(
        labelRes = R.string.heart_rate_zone_normal,
        color = Color(COLOR_NORMAL)
    ),
    ELEVATED(
        labelRes = R.string.heart_rate_zone_elevated,
        color = Color(COLOR_ELEVATED)
    ),
    CARDIO(
        labelRes = R.string.heart_rate_zone_cardio,
        color = Color(COLOR_CARDIO)
    ),
    PEAK(
        labelRes = R.string.heart_rate_zone_peak,
        color = Color(COLOR_PEAK)
    ),
    EXTREME(
        labelRes = R.string.heart_rate_zone_extreme,
        color = Color(COLOR_EXTREME)
    );

    companion object {
        /**
         * Classifies a heart rate value into a corresponding physiological zone.
         */
        fun classify(bpm: Int): HeartRateZone {
            return when {
                bpm < LOW_MAX_BPM -> LOW
                bpm <= NORMAL_MAX_BPM -> NORMAL
                bpm <= ELEVATED_MAX_BPM -> ELEVATED
                bpm <= CARDIO_MAX_BPM -> CARDIO
                bpm <= PEAK_MAX_BPM -> PEAK
                else -> EXTREME
            }
        }
    }
}
