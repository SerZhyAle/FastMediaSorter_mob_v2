package com.sza.fastmediasorter.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache

object ExtensionThumbnailGenerator {

    private const val DEFAULT_BITMAP_SIZE = 96
    private val cache = LruCache<String, Bitmap>(30)

    fun generate(extension: String, size: Int = DEFAULT_BITMAP_SIZE): Bitmap {
        val normalized = extension.uppercase().take(5).ifBlank { "FILE" }
        val cacheKey = "$normalized-$size"
        cache.get(cacheKey)?.let { return it }

        val bgColor = ThumbnailColorMapper.getColorForExtension(normalized)
        val textColor = ThumbnailColorMapper.getContrastingTextColor(bgColor)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            // Extension label is the primary information of a no-thumbnail tile, so it is
            // enlarged 1.5x over the prior 0.30 ratio for readability.
            textSize = size * 0.45f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val xPos = size / 2f
        val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(normalized, xPos, yPos, textPaint)

        cache.put(cacheKey, bitmap)
        return bitmap
    }

    fun clearCache() {
        cache.evictAll()
    }
}
