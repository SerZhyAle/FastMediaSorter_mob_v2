package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import java.util.Locale

/**
 * Labels for the translation/OCR language UI and flag glyphs for the streams COUNTRY UI.
 *
 * A language is marked by its own name only (ICON-EXTERNAL 0.11 rule 6), so the language functions
 * prefix nothing but the non-flag [LanguageItem.glyph]. A country keeps its flag: most use a Unicode
 * regional-indicator emoji ([TranslationLanguageCatalog.getFlagEmoji]); two have no Unicode codepoint
 * and are drawn from a vector drawable inlined into the text via an [ImageSpan]:
 *  - Russia ("RU")  -> white-blue-white flag (бело-сине-белый)
 *  - Belarus ("BY") -> white-red-white flag (бело-красно-белый)
 */
object LanguageFlagFormatter {

    private val customCountryFlags: Map<String, Int> = mapOf(
        "RU" to R.drawable.flag_white_blue_white,
        "BY" to R.drawable.flag_white_red_white
    )

    /** True when the country is rendered with a custom image flag instead of an emoji. */
    fun hasCustomCountryFlag(countryCode: String): Boolean = normalizeCountry(countryCode) in customCountryFlags

    /** Sets [view] text to the full label: "Localized (Native)". */
    fun applyLabel(view: TextView, item: LanguageItem) {
        timber.log.Timber.d("S4055: translation/OCR language label without flag for ${item.code}")
        view.text = plainLabel(item)
    }

    /** Compact label for narrow buttons: the upper-case language code, after the glyph when one exists. */
    fun compactLabel(item: LanguageItem?, code: String): CharSequence {
        val upperCode = code.uppercase(Locale.ROOT)
        if (item == null || item.glyph.isBlank()) return upperCode
        return "${item.glyph} $upperCode"
    }

    /** Compact country-chip label: custom image flag when configured, else emoji plus the ISO code. */
    fun compactCountryCodeLabel(view: TextView, countryCode: String): CharSequence {
        val normalized = normalizeCountry(countryCode)
        val imageFlag = customCountryFlagGlyph(view, normalized)
        if (imageFlag != null) {
            return TextUtils.concat(imageFlag, " ", normalized)
        }
        val emoji = TranslationLanguageCatalog.getFlagEmoji(normalized)
        return if (emoji.isBlank()) normalized else "$emoji $normalized"
    }

    /**
     * S0785: renders the flag glyph ONLY (no ISO code) for a country into [view] - used as the
     * streams-list leading-slot fallback when a channel has no favicon tile and as the country
     * picker's flag column. Custom image flags (RU/BY) are drawn via the same [ImageSpan] path as the
     * chips; every other country uses its Unicode regional-indicator emoji. Returns false when the code
     * maps to no flag, so the caller hides the slot instead of showing a blank glyph.
     */
    fun applyCountryFlagGlyph(view: TextView, countryCode: String): Boolean {
        val normalized = normalizeCountry(countryCode)
        val glyph: CharSequence = customCountryFlagGlyph(view, normalized)
            ?: TranslationLanguageCatalog.getFlagEmoji(normalized).takeIf { it.isNotBlank() }
            ?: return false
        view.text = glyph
        return true
    }

    /** Plain-text label, also used for content descriptions. */
    fun plainLabel(item: LanguageItem): String = TranslationLanguageCatalog.formatLanguage(item)

    private fun normalizeCountry(countryCode: String): String = countryCode.trim().uppercase(Locale.ROOT)

    private fun customCountryFlagGlyph(view: TextView, normalized: String): CharSequence? {
        val drawable = customCountryFlags[normalized]
            ?.let { res -> ContextCompat.getDrawable(view.context, res) }
            ?: return null
        val height = (view.textSize * 0.95f).toInt().coerceAtLeast(1)
        val width = (height * 1.5f).toInt().coerceAtLeast(1)
        drawable.setBounds(0, 0, width, height)
        return SpannableString(OBJECT_REPLACEMENT).apply {
            setSpan(CenteredImageSpan(drawable), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private const val OBJECT_REPLACEMENT = "￼"

    /**
     * [ImageSpan] that vertically centers the drawable on the text line.
     * DynamicDrawableSpan.ALIGN_CENTER is API 29+, but minSdk is 23 (legacy flavor),
     * so centering is implemented manually to work on every supported API level.
     */
    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val rect = drawable.bounds
            if (fm != null) {
                val pfm = paint.fontMetricsInt
                val fontHeight = pfm.descent - pfm.ascent
                val centerY = pfm.ascent + fontHeight / 2
                fm.ascent = centerY - rect.height() / 2
                fm.top = fm.ascent
                fm.descent = centerY + rect.height() / 2
                fm.bottom = fm.descent
            }
            return rect.right
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val pfm = paint.fontMetricsInt
            val fontHeight = pfm.descent - pfm.ascent
            val centerY = y + pfm.descent - fontHeight / 2
            val transY = centerY - drawable.bounds.height() / 2
            canvas.save()
            canvas.translate(x, transY.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}
