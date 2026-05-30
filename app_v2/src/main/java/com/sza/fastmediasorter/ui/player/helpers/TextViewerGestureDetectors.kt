package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import kotlin.math.abs

/** Builds the two `GestureDetector`s for [TextViewerManager]: text-content (font-size swipe + page nav + fullscreen-exit) and translation-overlay (font-size swipe + tap-toggle). Extracted to keep the host class under the 1000-LOC budget. */
internal object TextViewerGestureDetectors {

    private const val SWIPE_THRESHOLD_PERCENT = 0.05f
    private const val SWIPE_VELOCITY_THRESHOLD = 100

    fun buildTextDetector(
        context: Context,
        binding: ActivityPlayerUnifiedBinding,
        safeViews: PlayerBindingSafeViews,
        getCurrentFile: () -> com.sza.fastmediasorter.domain.model.MediaFile?,
        getTextFilePager: () -> TextFilePager?,
        onIncreaseTextFontSize: () -> Unit,
        onDecreaseTextFontSize: () -> Unit,
        onHideOcrText: () -> Unit,
        onNextPage: () -> Unit,
        onPreviousPage: () -> Unit,
        onExitFullscreenMode: () -> Unit,
    ): GestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val screenWidth = binding.root.width
            val screenHeight = binding.root.height
            val swipeThreshold = (minOf(screenWidth, screenHeight) * SWIPE_THRESHOLD_PERCENT).toInt().coerceAtLeast(50)
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            // Horizontal swipe -> font size adjustment.
            if (abs(diffX) > abs(diffY) && abs(diffX) > swipeThreshold && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) onIncreaseTextFontSize() else onDecreaseTextFontSize()
                return true
            }
            // In edit mode, vertical gestures must remain plain text scrolling.
            if (safeViews.textEditContainer.isVisible) return false
            // Vertical swipe -> page navigation or fullscreen exit.
            if (abs(diffY) > abs(diffX) && abs(diffY) > swipeThreshold && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                val scrollView = safeViews.textScrollView
                val isAtTop = !scrollView.canScrollVertically(-1)
                val isAtBottom = !scrollView.canScrollVertically(1)
                // OCR text (currentFile is null) closes only at scroll edges.
                if (getCurrentFile() == null && safeViews.textViewerContainer.isVisible) {
                    if (diffY < 0 && isAtBottom) { onHideOcrText(); return true }
                    if (diffY > 0 && isAtTop) { onHideOcrText(); return true }
                    return false
                }
                val pager = getTextFilePager()
                if (diffY < 0 && isAtBottom) {
                    if (pager != null && pager.hasNextPage()) { onNextPage(); return true }
                    onExitFullscreenMode(); return true
                } else if (diffY > 0 && isAtTop) {
                    if (pager != null && pager.hasPreviousPage()) { onPreviousPage(); return true }
                    onExitFullscreenMode(); return true
                }
            }
            return false
        }
    })

    fun buildTranslationDetector(
        context: Context,
        binding: ActivityPlayerUnifiedBinding,
        onIncreaseTranslationFontSize: () -> Unit,
        onDecreaseTranslationFontSize: () -> Unit,
        onToggleOverlaySize: () -> Unit,
    ): GestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val screenWidth = binding.root.width
            val screenHeight = binding.root.height
            val swipeThreshold = (minOf(screenWidth, screenHeight) * SWIPE_THRESHOLD_PERCENT).toInt().coerceAtLeast(50)
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y
            // Horizontal swipes only.
            if (abs(diffX) > abs(diffY) && abs(diffX) > swipeThreshold && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) onIncreaseTranslationFontSize() else onDecreaseTranslationFontSize()
                return true
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onToggleOverlaySize()
            return true
        }
    })
}
