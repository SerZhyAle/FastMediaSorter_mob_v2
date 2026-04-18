package com.sza.fastmediasorter.ui.player

import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.HslAdjustment
import timber.log.Timber

/**
 * Owns session-level video color adjustments that should survive player recreation.
 *
 * HUE is applied via Media3's HSL shader instead of a view overlay so the user sees
 * a real frame-level color transform with predictable behaviour in fullscreen, PiP,
 * and future effect composition.
 */
class VideoColorProcessor(
    initialHueDegrees: Float = 0f,
    initialBrightnessAdjustment: Float = 0f
) {

    @Volatile
    private var hueAdjustmentDegrees: Float = normalizeHue(initialHueDegrees)

    @Volatile
    private var brightnessAdjustment: Float = normalizeBrightness(initialBrightnessAdjustment)

    fun setHueAdjustmentDegrees(hueDegrees: Float) {
        val normalized = normalizeHue(hueDegrees)
        if (hueAdjustmentDegrees == normalized) return
        hueAdjustmentDegrees = normalized
        Timber.d("VideoColorProcessor: hueAdjustmentDegrees → $normalized")
    }

    fun getHueAdjustmentDegrees(): Float = hueAdjustmentDegrees

    fun setBrightnessAdjustment(adjustment: Float) {
        val normalized = normalizeBrightness(adjustment)
        if (brightnessAdjustment == normalized) return
        brightnessAdjustment = normalized
        Timber.d("VideoColorProcessor: brightnessAdjustment → $normalized")
    }

    fun getBrightnessAdjustment(): Float = brightnessAdjustment

    fun buildHueEffect(): Effect? {
        val hue = hueAdjustmentDegrees
        if (hue == 0f) return null

        return HslAdjustment.Builder()
            .adjustHue(hue)
            .build()
    }

    fun buildBrightnessEffect(): Effect? {
        val brightness = brightnessAdjustment
        if (brightness == 0f) return null
        return Brightness(brightness)
    }

    fun release() {
        hueAdjustmentDegrees = 0f
        brightnessAdjustment = 0f
        Timber.d("VideoColorProcessor: released")
    }

    private fun normalizeHue(hueDegrees: Float): Float {
        val wrapped = ((hueDegrees + 180f) % 360f + 360f) % 360f - 180f
        return if (wrapped == -180f) 180f else wrapped
    }

    private fun normalizeBrightness(adjustment: Float): Float = adjustment.coerceIn(-1f, 1f)
}