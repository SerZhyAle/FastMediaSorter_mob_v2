package com.sza.fastmediasorter.core.util

import android.graphics.Color

/**
 * Color palette for destination buttons (0-9)
 * According to specification: Each destination has its own color
 */
object ColorPalette {

    /**
     * Default color palette with good contrast for 10 destinations
     * Colors are ordered from 0 to 9
     */
    val DEFAULT_COLORS = intArrayOf(
        Color.parseColor("#4CAF50"), // 0 - Green
        Color.parseColor("#2196F3"), // 1 - Blue
        Color.parseColor("#FF9800"), // 2 - Orange
        Color.parseColor("#9C27B0"), // 3 - Purple
        Color.parseColor("#F44336"), // 4 - Red
        Color.parseColor("#00BCD4"), // 5 - Cyan
        Color.parseColor("#FFEB3B"), // 6 - Yellow
        Color.parseColor("#E91E63"), // 7 - Pink
        Color.parseColor("#795548"), // 8 - Brown
        Color.parseColor("#607D8B")  // 9 - Blue Grey
    )

    /**
     * Extended color palette for user selection
     * Includes more color options with good visibility
     */
    val EXTENDED_PALETTE = intArrayOf(
        // Row 1 - Primary colors
        Color.parseColor("#F44336"), // Red
        Color.parseColor("#E91E63"), // Pink
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#673AB7"), // Deep Purple
        Color.parseColor("#3F51B5"), // Indigo
        Color.parseColor("#2196F3"), // Blue
        
        // Row 2 - Cool colors
        Color.parseColor("#03A9F4"), // Light Blue
        Color.parseColor("#00BCD4"), // Cyan
        Color.parseColor("#009688"), // Teal
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#8BC34A"), // Light Green
        Color.parseColor("#66BB6A"), // Medium Green
        
        // Row 3 - Warm colors
        Color.parseColor("#CDDC39"), // Lime
        Color.parseColor("#FFEB3B"), // Yellow
        Color.parseColor("#FFC107"), // Amber
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#FF5722"), // Deep Orange
        Color.parseColor("#FF6F00"), // Dark Orange
        
        // Row 4 - Earth tones
        Color.parseColor("#795548"), // Brown
        Color.parseColor("#A1887F"), // Light Brown
        Color.parseColor("#9E9E9E"), // Grey
        Color.parseColor("#757575"), // Dark Grey
        Color.parseColor("#607D8B"), // Blue Grey
        Color.parseColor("#546E7A"), // Dark Blue Grey
        
        // Row 5 - Additional colors
        Color.parseColor("#000000"), // Black
        Color.parseColor("#FFFFFF"), // White
        Color.parseColor("#D32F2F"), // Dark Red
        Color.parseColor("#C2185B"), // Dark Pink
        Color.parseColor("#7B1FA2"), // Dark Purple
        Color.parseColor("#512DA8")  // Dark Indigo
    )

    /**
     * Get default color for destination by index (0-9)
     */
    fun getDefaultColor(index: Int): Int {
        return if (index in 0..9) {
            DEFAULT_COLORS[index]
        } else {
            DEFAULT_COLORS[0]
        }
    }

    /**
     * Find closest color name for display
     */
    fun getColorName(color: Int): String {
        return when (color) {
            Color.parseColor("#F44336") -> "Red"
            Color.parseColor("#E91E63") -> "Pink"
            Color.parseColor("#9C27B0") -> "Purple"
            Color.parseColor("#673AB7") -> "Deep Purple"
            Color.parseColor("#3F51B5") -> "Indigo"
            Color.parseColor("#2196F3") -> "Blue"
            Color.parseColor("#03A9F4") -> "Light Blue"
            Color.parseColor("#00BCD4") -> "Cyan"
            Color.parseColor("#009688") -> "Teal"
            Color.parseColor("#4CAF50") -> "Green"
            Color.parseColor("#8BC34A") -> "Light Green"
            Color.parseColor("#CDDC39") -> "Lime"
            Color.parseColor("#FFEB3B") -> "Yellow"
            Color.parseColor("#FFC107") -> "Amber"
            Color.parseColor("#FF9800") -> "Orange"
            Color.parseColor("#FF5722") -> "Deep Orange"
            Color.parseColor("#795548") -> "Brown"
            Color.parseColor("#A1887F") -> "Light Brown"
            Color.parseColor("#9E9E9E") -> "Grey"
            Color.parseColor("#757575") -> "Dark Grey"
            Color.parseColor("#607D8B") -> "Blue Grey"
            Color.parseColor("#546E7A") -> "Dark Blue Grey"
            Color.parseColor("#000000") -> "Black"
            Color.parseColor("#FFFFFF") -> "White"
            Color.parseColor("#D32F2F") -> "Dark Red"
            Color.parseColor("#C2185B") -> "Dark Pink"
            Color.parseColor("#7B1FA2") -> "Dark Purple"
            Color.parseColor("#512DA8") -> "Dark Indigo"
            Color.parseColor("#66BB6A") -> "Medium Green"
            Color.parseColor("#FF6F00") -> "Dark Orange"
            else -> "Custom"
        }
    }
}
