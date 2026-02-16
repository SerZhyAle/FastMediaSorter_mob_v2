package com.sza.fastmediasorter.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

/**
 * Generator for binary file thumbnails.
 * Creates programmatic thumbnails with extension text and gradient background.
 * 
 * Task 6: Binary file support
 */
class BinaryFileThumbnailGenerator(private val context: Context) {
    
    // LRU cache for generated thumbnails (key: "ext-type-size")
    private val cache = LruCache<String, Bitmap>(50)
    
    /**
     * Generate thumbnail for binary file
     * @param extension File extension (without dot)
     * @param type MediaType of binary file
     * @param size Thumbnail size in pixels (square)
     * @return Generated bitmap thumbnail
     */
    fun generateThumbnail(
        extension: String,
        type: MediaType,
        size: Int = 200
    ): Bitmap {
        val cacheKey = "$extension-$type-$size"
        cache.get(cacheKey)?.let { 
            Timber.d("Using cached thumbnail for $cacheKey")
            return it 
        }
        
        Timber.d("Generating new thumbnail for extension=$extension, type=$type, size=$size")
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Get gradient colors based on extension palette (SPEC_07)
        val baseColor = ThumbnailColorMapper.getColorForExtension(extension)
        val startColor = ThumbnailColorMapper.lighten(baseColor)
        val endColor = ThumbnailColorMapper.darken(baseColor)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                startColor, endColor,
                Shader.TileMode.CLAMP
            )
        }
        
        // Draw rounded rectangle background
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val cornerRadius = size * 0.1f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        // Draw extension text in center
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThumbnailColorMapper.getContrastingTextColor(baseColor)
            textSize = size * 0.25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        
        // Limit to 4 characters for display
        val ext = extension.uppercase().take(4)
        val xPos = size / 2f
        val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(ext, xPos, yPos, textPaint)
        
        // Optional: Add small icon indicator at bottom (could be enhanced later)
        drawTypeIndicator(canvas, type, size)
        
        cache.put(cacheKey, bitmap)
        Timber.d("Thumbnail generated and cached for $cacheKey")
        return bitmap
    }
    
    /**
     * Draw small type indicator at bottom of thumbnail
     */
    private fun drawTypeIndicator(canvas: Canvas, type: MediaType, size: Int) {
        val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 180
            textSize = size * 0.12f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        
        val indicatorText = when (type) {
            MediaType.BINARY_ARCHIVE -> "Archive"
            MediaType.BINARY_DISK -> "Disk"
            MediaType.BINARY_EXECUTABLE -> "App"
            else -> "Binary"
        }
        
        val xPos = size / 2f
        val yPos = size * 0.85f
        canvas.drawText(indicatorText, xPos, yPos, indicatorPaint)
    }
    
    /**
     * Clear thumbnail cache
     */
    fun clearCache() {
        cache.evictAll()
        Timber.d("Binary thumbnail cache cleared")
    }
    
    /**
     * Get current cache size
     */
    fun getCacheSize(): Int = cache.size()
}
