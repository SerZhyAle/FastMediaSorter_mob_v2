package com.sza.fastmediasorter.ui.icon

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.sza.fastmediasorter.core.icon.PlateContrast
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * S3433: the in-app decorated look (ICON-RENDER 0.10 section 10, items B and E) - the glyph, unchanged,
 * centred on a flat circle of its hue, its 24 grid spanning [GLYPH_SHARE] of the circle.
 *
 * The look is derived, never drawn: this wraps the same drawable the mono look shows and only recolours
 * it, so the plain and the decorated icon are recognisably one. A view tint does not reach it - a custom
 * Drawable ignores setTintList - so a recycled view's leftover tint cannot recolour a plate.
 */
class GlyphPlateDrawable(glyph: Drawable, @ColorInt plate: Int) : Drawable() {

    private val glyph: Drawable = glyph.mutate().also {
        DrawableCompat.setTint(it, PlateContrast.onPlateColour(plate))
    }

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = plate
        style = Paint.Style.FILL
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val glyphSide = (min(bounds.width(), bounds.height()) * GLYPH_SHARE).roundToInt()
        val left = bounds.centerX() - glyphSide / 2
        val top = bounds.centerY() - glyphSide / 2
        glyph.setBounds(left, top, left + glyphSide, top + glyphSide)
    }

    override fun draw(canvas: Canvas) {
        val radius = min(bounds.width(), bounds.height()) / 2f
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, platePaint)
        glyph.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        platePaint.alpha = alpha
        glyph.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        platePaint.colorFilter = colorFilter
        glyph.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = plateSide(glyph.intrinsicWidth)

    override fun getIntrinsicHeight(): Int = plateSide(glyph.intrinsicHeight)

    private fun plateSide(glyphSide: Int): Int =
        if (glyphSide <= 0) glyphSide else (glyphSide / GLYPH_SHARE).roundToInt()

    companion object {
        /** The share of the plate's side the glyph's 24 grid takes (contract item E). */
        const val GLYPH_SHARE = 0.6f

        /** [glyphRes] on a plate of [hueRes] resolved in [context]'s theme; null for a missing drawable. */
        fun of(context: Context, @DrawableRes glyphRes: Int, @ColorRes hueRes: Int): GlyphPlateDrawable? =
            AppCompatResources.getDrawable(context, glyphRes)?.let {
                GlyphPlateDrawable(it, ContextCompat.getColor(context, hueRes))
            }
    }
}
