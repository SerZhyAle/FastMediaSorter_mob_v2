package com.sza.fastmediasorter.ui.player.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber
import kotlin.math.abs

/**
 * Custom view for drawing translated text blocks in Google Lens style
 * Overlays translated text rectangles over original text positions
 * 
 * Language-agnostic design:
 * - Works equally well for any translation direction (ru→en, en→ru, uk→en, etc.)
 * - Font is sized to the original text so the block matches and covers the source
 * - Intelligent text wrapping with expansion limits
 *
 * Text rendering strategy (S0451):
 * 1. Auto-size font so a single line fills ~90% of the original OCR box height
 *    (metric-correct, no fixed sp ceiling) so it matches the source text size
 * 2. Wrap text to multiple lines within bounding box width
 * 3. S1713: a translation that does not fit grows the plate DOWNWARD - it is never shrunk to fit and
 *    never clipped, because a translation smaller than the line it replaces defeats the plate
 * 4. Background covers at least the original box and grows down as far as the view allows
 * 5. Center text vertically within final box
 * 
 * Features:
 * - Tap on a block to bring it to front (raise z-order)
 * - S1713: padding is a share of the type size (see PLATE_PADDING_EM), not of the box height
 * - Slight letter spacing for better readability
 * - Rounded corners and an OPAQUE backing (S1713 - at 94 % the source letters read through)
 */
class TranslationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Data class representing a translated text block with position
     */
    data class TranslatedBlock(
        val originalText: String,
        val translatedText: String,
        val boundingBox: Rect,
        val confidence: Float,
        // S1713: opaque. The plate exists to cover the source text; at 94 % the original letters read
        // through it, and if the result looks heavy the cause is the colour (S1704, S1714), not the alpha.
        var backgroundColor: Int = Color.parseColor("#FFFFFFFF"),
        var textColor: Int = Color.BLACK, // Contrast text color
        var customFontSize: Float? = null, // Per-block font size override (6-72sp)
        // S1711: source-line type size in OCR pixels (median of the line's word heights). Null means the
        // engine reported no words, and the size then comes from the box height as it always did.
        val typeSizePx: Int? = null
    )

    private val translatedBlocks = mutableListOf<TranslatedBlock>()
    
    // Minimum SP floor for translated text (prevents unreadably small text).
    private val minTextSizeSp = 8f
    // S0451: auto-size targets this fraction of the original OCR box height so the
    // translation matches and covers the source text, leaving slight headroom for
    // longer translations before they wrap. There is no fixed SP ceiling
    // (perBlockMaxFontSizeSp is the only safety cap), so large source text is matched.
    private val singleLineHeightFill = 0.9f
    
    // Per-block custom font size range (user adjustable via gestures)
    private val perBlockMinFontSizeSp = 6f
    private val perBlockMaxFontSizeSp = 72f
    private val perBlockFontSizeStepSp = 2f
    
    // Source bitmap for color sampling
    private var sourceBitmap: Bitmap? = null
    
    // Font size multiplier for user-adjustable scaling (0.7x to 1.5x)
    private var fontSizeMultiplier: Float = 1.0f
    private val minFontSizeMultiplier = 0.7f
    private val maxFontSizeMultiplier = 1.5f
    private val fontSizeStep = 0.1f
    
    // Paint for background rectangles
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#FFFFFFFF") // S1713: opaque backing, see TranslatedBlock.backgroundColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // TextPaint for multiline text (extends Paint with text layout features)
    private val textPaint = TextPaint().apply {
        color = Color.BLACK
        textSize = 32f // Will be adjusted based on bounding box height
        isAntiAlias = true
        isFakeBoldText = false
        // Slight letter spacing for better readability of both Cyrillic and Latin
        letterSpacing = 0.02f
    }

    // S1702: Paint for the debug image-display-rect boundary, hoisted out of onDraw
    // so it is allocated once instead of per frame.
    private val debugPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.YELLOW
        strokeWidth = 5f
    }
    
    /**
     * Scale factor to convert OCR bitmap coordinates to view coordinates
     */
    private var scaleX: Float = 1f
    private var scaleY: Float = 1f
    
    /**
     * Offset for image position within view (for letterboxing)
     * When image doesn't fill the entire view, we need to offset coordinates
     */
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    
    /**
     * Cached scaled rectangles for hit testing
     */
    private val scaledRects = mutableListOf<RectF>()
    
    /**
     * Gesture detector for swipe gestures and taps
     */
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val deltaX = e2.x - e1.x
            val deltaY = e2.y - e1.y
            
            Timber.d("TranslationOverlay: onFling deltaX=$deltaX, deltaY=$deltaY")
            
            // Require primarily horizontal movement
            if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 50) {
                // Find which block was swiped
                for (i in translatedBlocks.indices.reversed()) {
                    if (i < scaledRects.size && scaledRects[i].contains(e1.x, e1.y)) {
                        val block = translatedBlocks[i]
                        // S0451: seed manual resize from the current auto size (sp) when the
                        // block has no override yet, so the first swipe nudges from what is shown.
                        val currentSize = block.customFontSize ?: run {
                            val scaledBoxHeight = block.boundingBox.height() * scaleY
                            autoTextSizePx(scaledBoxHeight) / resources.displayMetrics.density
                        }
                        
                        val newSize = if (deltaX > 0) {
                            // Swipe right - increase
                            (currentSize + perBlockFontSizeStepSp).coerceAtMost(perBlockMaxFontSizeSp)
                        } else {
                            // Swipe left - decrease  
                            (currentSize - perBlockFontSizeStepSp).coerceAtLeast(perBlockMinFontSizeSp)
                        }
                        
                        block.customFontSize = newSize
                        Timber.d("Block font size changed: ${block.customFontSize}sp (was ${currentSize}sp)")
                        invalidate()
                        return true
                    }
                }
            }
            return false
        }
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val x = e.x
            val y = e.y
            
            // Find which block was tapped (iterate in reverse to check top blocks first)
            for (i in translatedBlocks.indices.reversed()) {
                if (i < scaledRects.size && scaledRects[i].contains(x, y)) {
                    // Move tapped block to end of list (top of z-order)
                    val block = translatedBlocks.removeAt(i)
                    translatedBlocks.add(block)
                    scaledRects.clear() // Force recalculation
                    invalidate()
                    Timber.d("Translation block tapped: brought to front")
                    return true
                }
            }
            return false
        }
    })
    
    /**
     * Convert SP to pixels
     */
    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    /**
     * S0451: auto font size (px) that makes a single line fill [singleLineHeightFill]
     * of the original OCR box height, so the translation matches and covers the source
     * text. Metric-correct: divides by the font line-height-per-em so the rendered line
     * (ascent+descent), not the raw em, fills the target. Applies the global multiplier
     * and clamps to [minTextSizeSp]..[perBlockMaxFontSizeSp]; no fixed mid-range ceiling.
     */
    private fun autoTextSizePx(usableBoxHeight: Float): Float {
        val saved = textPaint.textSize
        textPaint.textSize = 100f
        val fm = textPaint.fontMetrics
        val lineHeightPerEm = ((fm.descent - fm.ascent) / 100f).coerceAtLeast(0.1f)
        textPaint.textSize = saved
        val target = (usableBoxHeight.coerceAtLeast(1f) * singleLineHeightFill) / lineHeightPerEm
        return (target * fontSizeMultiplier).coerceIn(spToPx(minTextSizeSp), spToPx(perBlockMaxFontSizeSp))
    }
    
    /**
     * S1711: height the automatic type size is derived from, in view pixels.
     *
     * A block that carries a [TranslatedBlock.typeSizePx] uses it, so one tall artifact inside the line no
     * longer sets the size of the translation; a block without one falls back to [scaledBoxHeightPx], which
     * is the behaviour that shipped before this rule.
     */
    internal fun autoTextSizeSourcePx(block: TranslatedBlock, scaledBoxHeightPx: Float): Float {
        val carried = block.typeSizePx ?: return scaledBoxHeightPx
        return carried * scaleY
    }

    /**
     * Load font size multiplier from SharedPreferences
     */
    init {
        loadFontSizeMultiplierAsync()
    }

    private fun loadFontSizeMultiplierAsync() {
        Thread {
            try {
                val prefs = context.applicationContext
                    .getSharedPreferences("translation_settings", Context.MODE_PRIVATE)
                val loadedMultiplier = prefs.getFloat("font_size_multiplier", 1.0f)
                    .coerceIn(minFontSizeMultiplier, maxFontSizeMultiplier)

                post {
                    fontSizeMultiplier = loadedMultiplier
                    invalidate()
                }
            } catch (e: Exception) {
                Timber.w(e, "TranslationOverlay: Failed to load font size multiplier")
            }
        }.start()
    }
    
    /**
     * Increase font size for translation blocks
     */
    fun increaseFontSize() {
        fontSizeMultiplier = (fontSizeMultiplier + fontSizeStep)
            .coerceAtMost(maxFontSizeMultiplier)
        saveFontSize()
        invalidate() // Redraw with new font size
    }
    
    /**
     * Decrease font size for translation blocks
     */
    fun decreaseFontSize() {
        fontSizeMultiplier = (fontSizeMultiplier - fontSizeStep)
            .coerceAtLeast(minFontSizeMultiplier)
        saveFontSize()
        invalidate() // Redraw with new font size
    }
    
    /**
     * Get current font size multiplier
     */
    fun getFontSizeMultiplier(): Float = fontSizeMultiplier
    
    /**
     * Save font size multiplier to SharedPreferences
     */
    private fun saveFontSize() {
        val prefs = context.getSharedPreferences("translation_settings", Context.MODE_PRIVATE)
        prefs.edit().putFloat("font_size_multiplier", fontSizeMultiplier).apply()
    }
    
    /**
     * Create StaticLayout for multiline text with word wrapping
     */
    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    }
    
    private var imageDisplayRect: RectF? = null
    private var originalImageWidth: Int = 0
    private var originalImageHeight: Int = 0

    /**
     * Set the original dimensions of the source image.
     * Required for calculating scale relative to the displayed rect.
     */
    fun setOriginalImageSize(width: Int, height: Int) {
        this.originalImageWidth = width
        this.originalImageHeight = height
    }

    /**
     * Update the display rectangle of the image (where it is actually drawn on screen).
     * This handles zoom and pan transformations from PhotoView.
     */
    fun updateImageDisplayRect(rect: RectF) {
        this.imageDisplayRect = rect
        // Recalculate scale variables based on the current display rect
        if (originalImageWidth > 0 && originalImageHeight > 0) {
            scaleX = rect.width() / originalImageWidth.toFloat()
            scaleY = rect.height() / originalImageHeight.toFloat()
            offsetX = rect.left
            offsetY = rect.top
            Timber.d("TRANSLATION_DEBUG: updateImageDisplayRect: Rect=$rect")
            Timber.d("TRANSLATION_DEBUG: Scale Updated -> Orig: ${originalImageWidth}x${originalImageHeight} -> Scale: $scaleX, $scaleY -> Offset: $offsetX, $offsetY")
            Timber.d("TRANSLATION_DEBUG: Number of blocks to draw: ${translatedBlocks.size}")
        } else {
            Timber.d("TRANSLATION_DEBUG: Cannot update scale - original image size not set (${originalImageWidth}x${originalImageHeight})")
        }
        invalidate()
    }

    /**
     * Set the scale factor for coordinate conversion.
     * Calculates uniform scale (fit center) and offsets for letterboxing.
     * Call this when the bitmap size differs from the view size.
     */
    fun setScale(bitmapWidth: Int, bitmapHeight: Int, viewWidth: Int, viewHeight: Int) {
        setOriginalImageSize(bitmapWidth, bitmapHeight)
        
        // Calculate scale factors for each dimension
        val scaleToFitWidth = viewWidth.toFloat() / bitmapWidth.toFloat()
        val scaleToFitHeight = viewHeight.toFloat() / bitmapHeight.toFloat()
        
        // Use uniform scale (min of both) to maintain aspect ratio (fit center behavior)
        val uniformScale = minOf(scaleToFitWidth, scaleToFitHeight)
        scaleX = uniformScale
        scaleY = uniformScale
        
        // Calculate the actual size of scaled image
        val scaledImageWidth = bitmapWidth * uniformScale
        val scaledImageHeight = bitmapHeight * uniformScale
        
        // Calculate offsets for centering (letterboxing)
        offsetX = (viewWidth - scaledImageWidth) / 2f
        offsetY = (viewHeight - scaledImageHeight) / 2f
        
        // Update display rect to match this calculated state
        imageDisplayRect = RectF(offsetX, offsetY, offsetX + scaledImageWidth, offsetY + scaledImageHeight)
        Timber.d("DRAW_DEBUG: setScale -> Orig: ${bitmapWidth}x${bitmapHeight} -> View: ${viewWidth}x${viewHeight} -> Scale: $uniformScale -> Offset: $offsetX, $offsetY")
    }
    
    /**
     * Set the source bitmap for color sampling.
     * This is the full-resolution bitmap; bounding boxes are in OCR-bitmap
     * coordinates (see [setScale]/[setOriginalImageSize]).
     */
    fun setSourceBitmap(bitmap: Bitmap?) {
        sourceBitmap = bitmap
    }

    /**
     * Sample background color from source image at top-left corner of bounding box.
     *
     * The bounding box is measured on the (possibly down-scaled) OCR bitmap, while
     * [sourceBitmap] is the full-resolution original. Scale the OCR coordinates into
     * source-bitmap space before sampling so the plate colour is read from the point
     * actually under the plate (S1704).
     */
    private fun sampleBackgroundColor(boundingBox: Rect): Int {
        val bitmap = sourceBitmap ?: return Color.parseColor("#FFFFFFFF")

        try {
            val (x, y) = ocrPointToSource(boundingBox.left, boundingBox.top, bitmap)
            val pixelColor = bitmap.getPixel(x, y)

            // Add slight opacity for better blending
            val alpha = 240 // ~94% opacity
            return Color.argb(
                alpha,
                Color.red(pixelColor),
                Color.green(pixelColor),
                Color.blue(pixelColor)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to sample background color")
            return Color.parseColor("#FFFFFFFF")
        }
    }

    /**
     * Map a point from OCR-bitmap coordinates to source-bitmap coordinates.
     *
     * Returns the input unchanged (clamped) when the two bitmaps are the same size.
     * Exposed as a pure, testable helper (S1704).
     */
    internal fun ocrPointToSource(ocrX: Int, ocrY: Int, source: Bitmap): Pair<Int, Int> {
        val ocrW = if (originalImageWidth > 0) originalImageWidth else source.width
        val ocrH = if (originalImageHeight > 0) originalImageHeight else source.height
        val scaleX = source.width.toFloat() / ocrW.toFloat()
        val scaleY = source.height.toFloat() / ocrH.toFloat()
        val x = (ocrX * scaleX).toInt().coerceIn(0, source.width - 1)
        val y = (ocrY * scaleY).toInt().coerceIn(0, source.height - 1)
        return x to y
    }

    /**
     * Calculate contrast text color (black or white) based on background brightness
     * Uses luminance formula: 0.299*R + 0.587*G + 0.114*B
     */

    private fun getContrastTextColor(backgroundColor: Int): Int {
        val r = Color.red(backgroundColor)
        val g = Color.green(backgroundColor)
        val b = Color.blue(backgroundColor)
        
        // Calculate perceived brightness (0-255)
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        
        // Threshold at 128 (mid-point)
        // Dark background → white text, Light background → black text
        return if (luminance < 128) Color.WHITE else Color.BLACK
    }
    
    /**
     * Update the translated blocks to display
     */
    fun setTranslatedBlocks(blocks: List<TranslatedBlock>) {
        translatedBlocks.clear()

        // Sample colors for each block
        for (block in blocks) {
            val bgColor = sampleBackgroundColor(block.boundingBox)
            block.backgroundColor = bgColor
            block.textColor = getContrastTextColor(bgColor)
        }
        
        translatedBlocks.addAll(blocks)
        scaledRects.clear() // Clear cached rects, will be recalculated on draw
        invalidate() // Trigger redraw
    }
    
    /**
     * Clear all translated blocks
     */
    fun clear() {
        translatedBlocks.clear()
        scaledRects.clear()
        invalidate()
    }
    
    /**
     * Handle touch events - delegate to GestureDetector for swipes and taps
     * CRITICAL: Always consume touch events when visible to prevent them from
     * passing through to the image/document underneath
     */
    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Always pass ALL events to gesture detector (including DOWN, MOVE, UP)
        // This is critical for fling detection to work properly
        gestureDetector.onTouchEvent(event)
        
        // On ACTION_UP, check if click was outside all blocks - if so, hide overlay
        if (event.action == MotionEvent.ACTION_UP) {
            val clickedInsideBlock = scaledRects.any { rect ->
                rect.contains(event.x, event.y)
            }
            
            if (!clickedInsideBlock && scaledRects.isNotEmpty()) {
                Timber.d("TRANSLATION_DEBUG: Click outside blocks detected - hiding overlay")
                visibility = View.GONE
                clear()
                // Also hide the old-style overlay if present
                (parent as? android.view.ViewGroup)?.let { parentView ->
                    parentView.findViewById<View>(com.sza.fastmediasorter.R.id.translationOverlay)?.visibility = View.GONE
                    parentView.findViewById<View>(com.sza.fastmediasorter.R.id.translationOverlayBackground)?.visibility = View.GONE
                }
            }
        }
        
        // CRITICAL: Always consume touch events when overlay is visible
        // to prevent them from passing through to the image touch zones
        return true
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Clear and rebuild scaled rects for hit testing
        scaledRects.clear()
        
        // Draw each translated block
        for (block in translatedBlocks) {
            // Use block-specific colors
            backgroundPaint.color = block.backgroundColor
            textPaint.color = block.textColor
            
            val translatedText = block.translatedText
            
            // Standard scaling based on the updateImageDisplayRect values
            val scaledLeft = (block.boundingBox.left * scaleX) + offsetX
            val scaledTop = (block.boundingBox.top * scaleY) + offsetY
            val scaledWidth = block.boundingBox.width() * scaleX
            val scaledHeight = block.boundingBox.height() * scaleY // Use scaleY for height

            if (translatedBlocks.indexOf(block) == 0) {
                 Timber.d("TRANSLATION_DEBUG: Drawing Block[0] text='$translatedText'")
                 Timber.d("TRANSLATION_DEBUG: Original bbox: ${block.boundingBox}")
                 Timber.d("TRANSLATION_DEBUG: Scaled position: left=$scaledLeft, top=$scaledTop, width=$scaledWidth, height=$scaledHeight")
                 Timber.d("TRANSLATION_DEBUG: Scale factors: scaleX=$scaleX, scaleY=$scaleY")
                 Timber.d("TRANSLATION_DEBUG: Offsets: offsetX=$offsetX, offsetY=$offsetY")
                 Timber.d("TRANSLATION_DEBUG: View dimensions: ${width}x${height}")
            }

            // S1713: padding is a share of the type size, not of the box height. The neighbouring
            // project measured plate padding as load-bearing rather than cosmetic and bracketed it at
            // 0.05em to 0.28em; PLATE_PADDING_EM sits inside that bracket. The old 2..4sp rule read the
            // box height, so a tall box padded a small translation as if it were large.
            val padding = (autoTextSizeSourcePx(block, scaledHeight) * PLATE_PADDING_EM)
                .coerceAtLeast(spToPx(MIN_PLATE_PADDING_SP))
            val availableWidth = (scaledWidth - padding * 2).toInt().coerceAtLeast(1)
            
            // Use custom font size if set by user gesture, otherwise auto-size to the
            // original box height (S0451) so the translation matches and covers the source.
            var textSize = if (block.customFontSize != null) {
                spToPx(block.customFontSize!!)
            } else {
                autoTextSizePx(autoTextSizeSourcePx(block, scaledHeight) - padding * 2)
            }

            textPaint.textSize = textSize
            
            // Create StaticLayout for multiline text wrapping within box width
            var staticLayout = createStaticLayout(block.translatedText, textPaint, availableWidth)
            
            // S1713: a translation that does not fit grows the plate downward. The shrink-to-fit pass that
            // used to sit here made the translation smaller than the source line it replaces, which is the
            // opposite of what the plate is for; growth is handled below, where the height is computed.
            
            // Calculate final background dimensions
            // Width: use box width, unless single line is wider
            val textActualWidth = (0 until staticLayout.lineCount)
                .maxOfOrNull { staticLayout.getLineWidth(it) } ?: 0f
            
            // S0451: cover at least the original box width (never shrink below the source text).
            // S1713: the sideways cap of 270 % is gone with the growth direction it belonged to - a plate
            // that widens covers the picture beside the line, which the line never occupied.
            val actualRight = scaledLeft + (textActualWidth + padding * 2).coerceAtLeast(scaledWidth)

            // S0451: cover at least the original box height.
            // S1713: downward is the direction a plate may grow, and it grows as far as the translation
            // needs - the old 270 % ceiling cut the text off instead. The view's own bottom is the only
            // limit, because a plate past it is drawn nowhere.
            val textHeightWithPadding = staticLayout.height + padding * 2
            val availableDownwards = (height - scaledTop).coerceAtLeast(scaledHeight)
            val actualBottom = scaledTop + textHeightWithPadding.coerceIn(scaledHeight, availableDownwards)
            
            // Calculate actual final box height (for proper vertical centering)
            val finalBoxHeight = actualBottom - scaledTop
            
            val backgroundRect = RectF(
                scaledLeft,
                scaledTop,
                actualRight,
                actualBottom
            )
            
            // Store rect for hit testing
            scaledRects.add(backgroundRect)
            
            // Draw background rectangle with adaptive color and subtle shadow effect
            canvas.drawRoundRect(backgroundRect, 6f, 6f, backgroundPaint)
            
            // Draw multiline text using StaticLayout with adaptive text color
            canvas.save()
            // Vertically center text within FINAL box height
            val textStartY = scaledTop + (finalBoxHeight - staticLayout.height) / 2
            canvas.translate(scaledLeft + padding, textStartY)
            staticLayout.draw(canvas)
            canvas.restore()
        }


        // S1702: debug-only overlay-alignment diagnostic - kept behind BuildConfig.DEBUG
        // so release builds never draw the yellow frame over content.
        // Draw the image display rect boundary to verify alignment with PhotoView.
        // This helps diagnostics if the overlay appears shifted relative to the image.
        if (BuildConfig.DEBUG) {
            imageDisplayRect?.let { rect ->
                canvas.drawRect(rect, debugPaint)

                // Draw a crosshair at the top-left of the rect to verify origin
                canvas.drawLine(rect.left - 20, rect.top, rect.left + 20, rect.top, debugPaint)
                canvas.drawLine(rect.left, rect.top - 20, rect.left, rect.top + 20, debugPaint)
            }
        }
    }

    private companion object {
        /**
         * S1713: plate padding as a share of the type size. Inherited bracket from the neighbouring
         * project's measurement (0.05em to 0.28em, where padding alone moved a scene from 0.2841 to
         * 0.2705 with byte-identical rectangles); this sits mid-bracket. Not derived on our own
         * material - S1716's harness is what would derive it.
         */
        const val PLATE_PADDING_EM = 0.12f

        /** Floor so a plate around very small text still has a visible edge. */
        const val MIN_PLATE_PADDING_SP = 2f
    }
}
