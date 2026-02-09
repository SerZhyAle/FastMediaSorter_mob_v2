package com.sza.fastmediasorter.core.util

/**
 * Utility object for managing destination colors.
 * Provides 10 distinct colors for destinations (orders 1-10).
 */
object DestinationColors {
    
    /**
     * Predefined colors for destinations 1-10
     * Colors are vibrant and easily distinguishable
     */
    private val DESTINATION_COLORS = listOf(
        0xFFE91E63.toInt(), // Pink (1)
        0xFF9C27B0.toInt(), // Purple (2)
        0xFF673AB7.toInt(), // Deep Purple (3)
        0xFF3F51B5.toInt(), // Indigo (4)
        0xFF2196F3.toInt(), // Blue (5)
        0xFF00BCD4.toInt(), // Cyan (6)
        0xFF4CAF50.toInt(), // Green (7)
        0xFFFFEB3B.toInt(), // Yellow (8)
        0xFFFF9800.toInt(), // Orange (9)
        0xFFF44336.toInt()  // Red (10)
    )
    
    /**
     * Get color for destination by order number (0-9)
     * Returns default green color if order is invalid
     */
    fun getColorForDestination(destinationOrder: Int?): Int {
        if (destinationOrder == null || destinationOrder < 0 || destinationOrder > 9) {
            return 0xFF4CAF50.toInt() // Default green
        }
        return DESTINATION_COLORS[destinationOrder]
    }
    
    /**
     * Get all available colors
     */
    fun getAllColors(): List<Int> = DESTINATION_COLORS
}
