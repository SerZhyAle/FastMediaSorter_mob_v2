package com.sza.fastmediasorter.ui.player.helpers

import timber.log.Timber

/**
 * Generates dynamic CSS for EPUB reader based on configurable style parameters.
 * Replaces hardcoded CSS in preprocessHtml().
 */
object EpubStyleManager {

    /**
     * Available reader themes with color schemes.
     */
    enum class ReaderTheme(
        val bgColor: String,
        val textColor: String,
        val linkColor: String,
        val displayName: String
    ) {
        LIGHT("#FFFFFF", "#000000", "#1976D2", "Light"),
        DARK("#1E1E1E", "#E0E0E0", "#64B5F6", "Dark"),
        SEPIA("#F5E6CC", "#5D4037", "#8D6E63", "Sepia"),
        OLED_BLACK("#000000", "#CCCCCC", "#4FC3F7", "OLED Black");

        companion object {
            fun fromName(name: String): ReaderTheme {
                return entries.find { it.name.equals(name, ignoreCase = true) } ?: LIGHT
            }
        }
    }

    /**
     * Generate complete CSS style block for EPUB content.
     *
     * @param theme Reader color theme
     * @param fontSizePx Font size in pixels
     * @param fontFamily CSS font-family value
     * @param lineHeight Line height multiplier (e.g. 1.6)
     * @param horizontalPaddingPx Horizontal padding in pixels
     * @return Complete <style> tag with CSS
     */
    fun generateCss(
        theme: ReaderTheme,
        fontSizePx: Int,
        fontFamily: String,
        lineHeight: Float,
        horizontalPaddingPx: Int
    ): String {
        return buildString {
            append("<style type='text/css'>")

            // Base body styles
            append("body {")
            append("background-color: ${theme.bgColor};")
            append("color: ${theme.textColor};")
            append("font-family: $fontFamily;")
            append("font-size: ${fontSizePx}px;")
            append("line-height: $lineHeight;")
            append("padding: 16px ${horizontalPaddingPx}px;")
            append("margin: 0;")
            append("word-wrap: break-word;")
            append("overflow-wrap: break-word;")
            append("}")

            // Link colors
            append("a { color: ${theme.linkColor}; }")

            // Headings — slightly larger, with spacing
            append("h1, h2, h3, h4, h5, h6 {")
            append("color: ${theme.textColor};")
            append("margin-top: 1em; margin-bottom: 0.5em;")
            append("}")

            // Image handling
            append("img {")
            append("max-width: 100%;")
            append("max-height: 100vh;")
            append("width: auto;")
            append("height: auto;")
            append("object-fit: contain;")
            append("display: block;")
            append("margin: 16px auto;")
            append("}")

            // Code blocks
            append("pre, code {")
            append("font-family: 'Courier New', monospace;")
            if (theme == ReaderTheme.DARK || theme == ReaderTheme.OLED_BLACK) {
                append("background-color: #2D2D2D;")
            } else if (theme == ReaderTheme.SEPIA) {
                append("background-color: #E8D5B7;")
            } else {
                append("background-color: #F5F5F5;")
            }
            append("padding: 4px 8px;")
            append("border-radius: 4px;")
            append("overflow-x: auto;")
            append("}")

            // Blockquote
            append("blockquote {")
            append("border-left: 3px solid ${theme.linkColor};")
            append("margin-left: 0;")
            append("padding-left: 16px;")
            append("opacity: 0.85;")
            append("}")

            // Tables
            append("table { border-collapse: collapse; width: 100%; }")
            append("td, th {")
            append("border: 1px solid ${theme.textColor}40;")
            append("padding: 8px;")
            append("}")

            append("</style>")
        }.also {
            Timber.d("EPUB CSS: theme=${theme.name}, font=${fontSizePx}px, lh=$lineHeight, margin=${horizontalPaddingPx}px")
        }
    }
}
