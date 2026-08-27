package com.sza.fastmediasorter.ui.launcher.tray

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.LauncherTrayIconBinding

/**
 * S2023: one tray slot, drawing a [LauncherTrayIconModel] and nothing else.
 *
 * Holds no source and reads no platform state - strategic §5 keeps the sources apart from the presentation
 * so the mapping between them stays coverable by JVM tests, which nothing in this class is.
 *
 * Carries the click, focus and foreground the bare `ImageView` slots carried before it, because this row is
 * read by a D-pad as often as by a finger (S1767, CLAUDE.md Rule 16).
 */
class LauncherTrayIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = LauncherTrayIconBinding.inflate(LayoutInflater.from(context), this)

    /** Captured before any highlight recolours the glyph, so "back to normal" needs no theme lookup. */
    private val defaultGlyphTint: ColorStateList? = ImageViewCompat.getImageTintList(binding.trayIconGlyph)

    init {
        isClickable = true
        isFocusable = true
        foreground = ContextCompat.getDrawable(context, R.drawable.focus_button_background)
    }

    fun apply(model: LauncherTrayIconModel) {
        binding.trayIconGlyph.setImageResource(model.iconRes)
        ImageViewCompat.setImageTintList(binding.trayIconGlyph, glyphTint(model.highlighted))
        binding.trayIconBadge.text = model.badge
        binding.trayIconBadge.isVisible = model.badge != null
        binding.trayIconCornerMarker.isVisible = model.cornerMarked
        contentDescription = model.contentDescription
    }

    /** Sets the glyph's image level, which is how the SIM slots draw their signal level-list drawable. */
    fun setGlyphLevel(level: Int) {
        binding.trayIconGlyph.setImageLevel(level)
    }

    private fun glyphTint(highlighted: Boolean): ColorStateList? = if (highlighted) {
        ColorStateList.valueOf(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
    } else {
        defaultGlyphTint
    }
}
