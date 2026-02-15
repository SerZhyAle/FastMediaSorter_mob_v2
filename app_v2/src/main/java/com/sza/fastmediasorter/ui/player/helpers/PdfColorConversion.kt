package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * Color conversion filters for PDF viewing comfort.
 * Provides night mode (invert) and sepia filters applied via ColorMatrixColorFilter.
 */
object PdfColorConversion {

    /**
     * PDF color modes for reading comfort.
     */
    enum class PdfColorMode {
        NORMAL,
        NIGHT,
        SEPIA
    }

    /**
     * Negative/invert color matrix for night mode.
     * Inverts RGB channels while keeping alpha unchanged.
     */
    private val NEGATIVE_MATRIX = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /**
     * Sepia tone color matrix for warm reading experience.
     */
    private val SEPIA_MATRIX = ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    /**
     * Get ColorMatrixColorFilter for the given mode.
     * Returns null for NORMAL mode (no filter).
     */
    fun getColorFilter(mode: PdfColorMode): ColorMatrixColorFilter? {
        return when (mode) {
            PdfColorMode.NORMAL -> null
            PdfColorMode.NIGHT -> ColorMatrixColorFilter(NEGATIVE_MATRIX)
            PdfColorMode.SEPIA -> ColorMatrixColorFilter(SEPIA_MATRIX)
        }
    }

    /**
     * Cycle to next color mode: NORMAL → NIGHT → SEPIA → NORMAL.
     */
    fun nextMode(current: PdfColorMode): PdfColorMode {
        return when (current) {
            PdfColorMode.NORMAL -> PdfColorMode.NIGHT
            PdfColorMode.NIGHT -> PdfColorMode.SEPIA
            PdfColorMode.SEPIA -> PdfColorMode.NORMAL
        }
    }

    /**
     * Parse mode from persisted string name. Defaults to NORMAL.
     */
    fun fromName(name: String): PdfColorMode {
        return try {
            PdfColorMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            PdfColorMode.NORMAL
        }
    }
}
