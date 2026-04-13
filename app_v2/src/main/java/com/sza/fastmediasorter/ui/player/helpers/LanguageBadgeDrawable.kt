package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue

/**
 * Custom Drawable that displays source and target language codes in a 2x2 grid.
 * Used for translation buttons in PlayerActivity.
 * 
 * Layout:
 * Top row: Source language (2 chars ISO) or "A" for auto
 * Bottom row: Target language (2 chars ISO)
 * 
 * Uses theme-aware color (colorControlNormal) for consistent appearance
 * in both light and dark themes.
 */
class LanguageBadgeDrawable(
    private val context: Context,
    private var sourceLang: String,
    private var targetLang: String,
    private val forcedColor: Int? = null
) : Drawable() {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = forcedColor ?: resolveThemeColor(context)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun resolveThemeColor(context: Context): Int {
        val typedValue = TypedValue()
        // Try colorControlNormal first (works for both light/dark themes)
        if (context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            return typedValue.data
        }
        // Fallback to textColorSecondary
        if (context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) {
            return typedValue.data
        }
        // Ultimate fallback - gray that works on both themes
        return 0xFF757575.toInt()
    }

    fun updateLanguages(source: String, target: String) {
        this.sourceLang = source
        this.targetLang = target
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        @Suppress("UNUSED_VARIABLE")
        val width = bounds.width()
        val height = bounds.height()
        
        // No background - transparent, only text with theme color

        // Prepare text
        val topText = if (sourceLang.equals("auto", ignoreCase = true)) "A" else sourceLang.uppercase().take(2)
        val bottomText = targetLang.uppercase().take(2)

        // Calculate font size based on height (approx 40% of height per line)
        // For 32dp icon, height is ~32px (mdpi) to ~128px (xxxhdpi).
        // We want the text to fill most of the height.
        val fontSize = height * 0.45f
        textPaint.textSize = fontSize

        // Calculate positions
        val centerX = bounds.centerX().toFloat()
        
        // Measure text height to center it vertically in its half
        val textBounds = Rect()
        textPaint.getTextBounds("A", 0, 1, textBounds)
        val textHeight = textBounds.height()
        
        // Top half center Y
        val topHalfCenterY = bounds.top + (height / 4f)
        val bottomHalfCenterY = bounds.top + (height * 3f / 4f)

        // Draw text centered in their respective halves
        // textPaint.textAlign is CENTER, so x is centerX
        // y is baseline. baseline = centerY + textHeight/2
        
        // Add a small padding between lines if needed, but 0.45f size should be fine
        val topBaseline = topHalfCenterY + (textHeight / 2f)
        val bottomBaseline = bottomHalfCenterY + (textHeight / 2f)

        canvas.drawText(topText, centerX, topBaseline, textPaint)
        canvas.drawText(bottomText, centerX, bottomBaseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    
    // CRITICAL: PopupMenu requires intrinsic size for icons to display
    override fun getIntrinsicWidth(): Int = (context.resources.displayMetrics.density * 24).toInt()
    
    override fun getIntrinsicHeight(): Int = (context.resources.displayMetrics.density * 24).toInt()
}
