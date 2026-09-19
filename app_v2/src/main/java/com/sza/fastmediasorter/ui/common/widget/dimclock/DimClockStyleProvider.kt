package com.sza.fastmediasorter.ui.common.widget.dimclock

import androidx.annotation.ColorInt

/**
 * Visual styling for the dim screen clock, mirroring the launcher clock gadget.
 */
data class DimClockStyle(
    @ColorInt val dialColor: Int? = null,
    val dialTypeface: String = DEFAULT_TYPEFACE,
    val secondsVisible: Boolean = false
) {
    companion object {
        // Mirrors ClockGadgetStateStore.DEFAULT_DIAL_TYPEFACE, the launcher clock's own default.
        const val DEFAULT_TYPEFACE = "default"
        val DEFAULT = DimClockStyle()
    }
}

/**
 * S3256: Provider for dim clock visual style.
 */
interface DimClockStyleProvider {
    fun getStyle(): DimClockStyle
    val secondsVisible: Boolean get() = getStyle().secondsVisible
}
