package com.sza.fastmediasorter.domain.models

/**
 * Font size options for translation and OCR results
 * Values represent multipliers for base text size
 */
enum class TranslationFontSize(val multiplier: Float, val stringResId: Int) {
    AUTO(1.0f, com.sza.fastmediasorter.R.string.font_size_auto),
    MINIMUM(0.7f, com.sza.fastmediasorter.R.string.font_size_minimum),
    SMALL(0.8f, com.sza.fastmediasorter.R.string.font_size_small),
    MEDIUM(1.0f, com.sza.fastmediasorter.R.string.font_size_medium),
    LARGE(1.5f, com.sza.fastmediasorter.R.string.font_size_large),
    HUGE(2.5f, com.sza.fastmediasorter.R.string.font_size_huge);
    
    companion object {
        fun fromMultiplier(value: Float): TranslationFontSize {
            return values().minByOrNull { kotlin.math.abs(it.multiplier - value) } ?: AUTO
        }
    }
}

/**
 * Font family options for translation and OCR results
 */
enum class TranslationFontFamily(val stringResId: Int, val typefaceName: String) {
    DEFAULT(com.sza.fastmediasorter.R.string.font_family_default, "sans-serif"),
    SERIF(com.sza.fastmediasorter.R.string.font_family_serif, "serif"),
    MONOSPACE(com.sza.fastmediasorter.R.string.font_family_monospace, "monospace");
    
    companion object {
        fun fromTypefaceName(name: String): TranslationFontFamily {
            return values().firstOrNull { it.typefaceName == name } ?: DEFAULT
        }
    }
}

/**
 * Session-scoped translation settings (reset when exiting Browse/Resource)
 */
data class TranslationSessionSettings(
    val fontSize: TranslationFontSize = TranslationFontSize.AUTO,
    val fontFamily: TranslationFontFamily = TranslationFontFamily.DEFAULT  // Default to system font
)
