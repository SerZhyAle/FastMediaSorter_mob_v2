package com.sza.fastmediasorter.ui.player.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.github.chrisbanes.photoview.PhotoView
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.hypot

/**
 * S1273/S1274: turns PDF pages straight from the touch stream.
 *
 * PhotoView cannot deliver these gestures: PhotoViewAttacher drops its single-fling callback
 * above scale 1.0, with more than one pointer, and below the platform fling velocity - which is
 * every state this detector exists for. Two ways to turn a page:
 *
 * - two fingers travelling vertically together, which works while zoomed and leaves the
 *   one-finger pan of a zoomed page untouched;
 * - one finger on an unzoomed page, with no velocity requirement at all, so a slow deliberate
 *   drag pages instead of doing nothing.
 */
class PdfPageSwipeDetector(
    context: Context,
    private val host: Host
) {

    interface Host {
        /** True while a swipe may turn a page - PDF open and not in scroll mode. */
        fun isPageSwipeEnabled(): Boolean

        /** Zoom of the surface this detector serves; each surface is judged on its own scale. */
        fun currentScale(): Float

        /** Turn one page; [next] false means the previous page. */
        fun turnPage(next: Boolean)
    }

    private val pagingSlop = ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()

    private var claimed = false
    private var pinchRejected = false
    private var firstPointerId = INVALID_POINTER
    private var firstDownX = 0f
    private var firstDownY = 0f
    private var secondPointerId = INVALID_POINTER
    private var secondDownX = 0f
    private var secondDownY = 0f
    private var initialSpan = 0f

    /**
     * Feed one touch event. Returns true once this gesture has been claimed as a page turn, which
     * tells the caller to stop handing the rest of the gesture to the PhotoView attacher.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> reset(event)
            MotionEvent.ACTION_POINTER_DOWN -> trackSecondPointer(event)
            MotionEvent.ACTION_MOVE -> if (!claimed && host.isPageSwipeEnabled()) {
                detectPageTurn(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasClaimed = claimed
                clear()
                return wasClaimed
            }
        }
        return claimed
    }

    private fun detectPageTurn(event: MotionEvent) {
        if (event.pointerCount >= TWO_POINTERS) {
            detectTwoFingerPageTurn(event)
        } else {
            detectOneFingerPageTurn(event)
        }
    }

    private fun detectTwoFingerPageTurn(event: MotionEvent) {
        if (pinchRejected || initialSpan <= 0f) return

        val firstIndex = event.findPointerIndex(firstPointerId)
        val secondIndex = event.findPointerIndex(secondPointerId)
        if (firstIndex < 0 || secondIndex < 0) return

        val span = hypot(
            event.getX(secondIndex) - event.getX(firstIndex),
            event.getY(secondIndex) - event.getY(firstIndex)
        )
        if (abs(span / initialSpan - 1f) > PINCH_SPAN_TOLERANCE) {
            // Fingers separating or closing - a pinch owns the rest of the gesture (S1274 3.1).
            pinchRejected = true
        } else {
            val firstDy = event.getY(firstIndex) - firstDownY
            val secondDy = event.getY(secondIndex) - secondDownY
            val travel = minOf(abs(firstDy), abs(secondDy))
            val drift = maxOf(
                abs(event.getX(firstIndex) - firstDownX),
                abs(event.getX(secondIndex) - secondDownX)
            )
            if ((firstDy < 0f) == (secondDy < 0f) && travel > pagingSlop && travel > drift) {
                // S1274: the zoomed-in page turn.
                claim(next = firstDy < 0f)
            }
        }
    }

    private fun detectOneFingerPageTurn(event: MotionEvent) {
        val index = event.findPointerIndex(firstPointerId)
        if (index < 0 || host.currentScale() > PAGE_PAN_SCALE_THRESHOLD) return

        val dy = event.getY(index) - firstDownY
        val dx = event.getX(index) - firstDownX
        if (abs(dy) > pagingSlop && abs(dy) > abs(dx)) {
            // S1274: the slow unzoomed drag, with no velocity requirement.
            claim(next = dy < 0f)
        }
    }

    private fun claim(next: Boolean) {
        claimed = true
        host.turnPage(next)
    }

    private fun trackSecondPointer(event: MotionEvent) {
        val firstIndex = event.findPointerIndex(firstPointerId)
        if (claimed || secondPointerId != INVALID_POINTER || firstIndex < 0) return

        val index = event.actionIndex
        secondPointerId = event.getPointerId(index)
        secondDownX = event.getX(index)
        secondDownY = event.getY(index)
        // Re-anchor the first pointer: the two-finger gesture starts now, not where it landed.
        firstDownX = event.getX(firstIndex)
        firstDownY = event.getY(firstIndex)
        initialSpan = hypot(secondDownX - firstDownX, secondDownY - firstDownY)
    }

    private fun reset(event: MotionEvent) {
        clear()
        firstPointerId = event.getPointerId(0)
        firstDownX = event.x
        firstDownY = event.y
    }

    private fun clear() {
        claimed = false
        pinchRejected = false
        firstPointerId = INVALID_POINTER
        secondPointerId = INVALID_POINTER
        initialSpan = 0f
    }

    companion object {
        /**
         * Wire a detector into [photoView] ahead of its PhotoView attacher, which is the only place
         * these gestures are visible. Anything the detector does not claim reaches the attacher
         * untouched, so pinch, pan and the fling callbacks behave exactly as before. [onDown] lets a
         * host keep recording its own touch-down point.
         *
         * One installer instead of three copies: every PDF host needs the same glue, and a copy that
         * drifts is how one screen ends up paging and another not.
         */
        @SuppressLint("ClickableViewAccessibility")
        fun install(photoView: PhotoView, host: Host, onDown: ((MotionEvent) -> Unit)? = null) {
            val detector = PdfPageSwipeDetector(photoView.context, host)
            val attacher = photoView.attacher
            var attacherCancelled = false
            photoView.setOnTouchListener { v, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    attacherCancelled = false
                    onDown?.invoke(ev)
                }
                if (detector.onTouchEvent(ev)) {
                    if (!attacherCancelled) {
                        attacherCancelled = true
                        // Cancel the attacher once, so a zoomed page stops panning under the fingers
                        // that just turned it and the trailing fling cannot turn a second page.
                        val cancelEvent = MotionEvent.obtain(ev)
                        cancelEvent.action = MotionEvent.ACTION_CANCEL
                        attacher.onTouch(v, cancelEvent)
                        cancelEvent.recycle()
                    }
                    true
                } else {
                    attacher.onTouch(v, ev)
                }
            }
        }

        private const val INVALID_POINTER = -1
        private const val TWO_POINTERS = 2

        /** Above this zoom a one-finger drag pans the page instead of turning it. */
        private const val PAGE_PAN_SCALE_THRESHOLD = 1.05f

        /** How far the span between two fingers may drift before the gesture counts as a pinch. */
        private const val PINCH_SPAN_TOLERANCE = 0.15f
    }
}
