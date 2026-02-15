package com.sza.fastmediasorter.ui.player.helpers

/**
 * Reader themes for text viewer, independent of system dark/light mode.
 */
enum class TextReaderTheme(
    val bgColor: Int,
    val textColor: Int,
    val labelResId: Int = 0 // Will use display name, not resource
) {
    LIGHT(0xFFFFFFFF.toInt(), 0xFF212121.toInt()),
    DARK(0xFF1E1E1E.toInt(), 0xFFD4D4D4.toInt()),
    SEPIA(0xFFF5E6CC.toInt(), 0xFF5D4037.toInt());

    companion object {
        fun fromName(name: String): TextReaderTheme {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: LIGHT
        }
    }
}
