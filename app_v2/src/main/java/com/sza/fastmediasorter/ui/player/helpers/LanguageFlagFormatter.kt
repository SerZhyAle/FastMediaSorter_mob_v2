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
 * Renders the per-language flag marker for the translation/OCR language UI.
 *
 * Most languages use a Unicode regional-indicator emoji (see [TranslationLanguageCatalog.getFlagEmoji]).
 * A few languages are represented by flags that have no Unicode codepoint and therefore must be drawn
 * from a vector drawable inlined into the text via an [ImageSpan]:
 *  - Russian ("ru")    -> white-blue-white flag (бело-сине-белый)
 *  - Belarusian ("be") -> white-red-white flag (бело-красно-белый)
 *
 * The catalog blanks the emoji for these codes, so [LanguageItem.flagEmoji] never carries a state flag
 * for them; this formatter supplies the image glyph instead and is the single source of the mapping.
 */
object LanguageFlagFormatter {

    private val customFlags: Map<String, Int> = mapOf(
        "ru" to R.drawable.flag_white_blue_white,
        "be" to R.drawable.flag_white_red_white
    )
    private val customCountryFlagLanguageCodes: Map<String, String> = mapOf(
        "RU" to "ru",
        "BY" to "be"
    )

    /** True when the language code is rendered with a custom image flag instead of an emoji. */
    fun hasCustomFlag(code: String): Boolean = code.lowercase(Locale.ROOT) in customFlags

    private fun flagDrawableRes(code: String): Int? = customFlags[code.lowercase(Locale.ROOT)]

    /** Sets [view] text to the full label: flag glyph + "Localized (Native)". */
    fun applyLabel(view: TextView, item: LanguageItem) {
        view.text = label(view, item)
    }

    /** Sets [view] text to the flag glyph only (used by the picker's dedicated flag column). */
    fun applyFlagGlyph(view: TextView, item: LanguageItem) {
        view.text = flagGlyph(view, item)
    }

    /** Full label for a list/spinner row: flag glyph followed by the localized and native names. */
    fun label(view: TextView, item: LanguageItem): CharSequence {
        val name = "${item.localizedName} (${item.nativeName})"
        return prefixFlag(view, item, name)
    }

    /** Compact label for narrow buttons: flag glyph followed by the upper-case language code. */
    fun compactLabel(view: TextView, item: LanguageItem?, code: String): CharSequence {
        val upperCode = code.uppercase(Locale.ROOT)
        if (item == null) return upperCode
        return prefixFlag(view, item, upperCode)
    }

    /** Compact country-chip label: custom image flag when configured, else emoji plus the ISO code. */
    fun compactCountryCodeLabel(view: TextView, countryCode: String): CharSequence {
        val normalized = countryCode.trim().uppercase(Locale.ROOT)
        val customItem = customCountryFlagItem(normalized)
        if (customItem != null) {
            return compactLabel(view, customItem, normalized)
        }
        val emoji = TranslationLanguageCatalog.getFlagEmoji(normalized)
        return if (emoji.isBlank()) normalized else "$emoji $normalized"
    }

    /** Custom image-flag item for a country code (used by streams-country pickers/chips). */
    fun customCountryFlagItem(
        countryCode: String,
        displayLocale: Locale = Locale.getDefault()
    ): LanguageItem? {
        val languageCode = customCountryFlagLanguageCodes[countryCode.trim().uppercase(Locale.ROOT)] ?: return null
        return TranslationLanguageCatalog.findLanguage(languageCode, displayLocale)
    }

    /** Plain-text equivalent of [label] for content descriptions (no image span, accessibility-safe). */
    fun plainLabel(item: LanguageItem): String {
        val name = "${item.localizedName} (${item.nativeName})"
        return if (item.flagEmoji.isBlank()) name else "${item.flagEmoji} $name"
    }

    private fun prefixFlag(view: TextView, item: LanguageItem, text: CharSequence): CharSequence {
        val glyph = flagGlyph(view, item)
        return if (hasCustomFlag(item.code)) {
            TextUtils.concat(glyph, " ", text)
        } else if (item.flagEmoji.isBlank()) {
            text
        } else {
            "${item.flagEmoji} $text"
        }
    }

    private fun flagGlyph(view: TextView, item: LanguageItem): CharSequence {
        val res = flagDrawableRes(item.code) ?: return item.flagEmoji
        val drawable = ContextCompat.getDrawable(view.context, res) ?: return item.flagEmoji
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
