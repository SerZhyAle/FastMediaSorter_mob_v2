package com.sza.fastmediasorter.ui.launcher.grid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * S1430: a scroll thumb for the launcher desktop that can actually be dragged.
 *
 * The system scrollbar of a scroll container is drawn but never touchable, and the platform has no attribute
 * that turns it into a control, so the desktop - which cannot become a `RecyclerView` (S0404 ADR-9) - needs
 * its own. The drawn track keeps the width and the colour of the bar it replaces; the grip comes from this
 * view being wider than that track (strategic ADR-2), so a touch lands easily without the bar growing.
 *
 * The view owns no scroll state: [onScrollPositionChanged] recomputes the thumb from the container, and a
 * drag reports back through [onScrollRequested] instead of remembering a position of its own.
 */
class LauncherScrollThumbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Called while the user drags the thumb, with the scroll position the container should take. */
    var onScrollRequested: ((Int) -> Unit)? = null

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scrollbar_thumb)
    }
    private val trackWidthPx = resources.getDimensionPixelSize(R.dimen.launcher_scroll_thumb_track_width)
    private val minThumbHeightPx = resources.getDimensionPixelSize(R.dimen.launcher_scroll_thumb_min_height)
    private val thumbRect = RectF()

    private var scrollY = 0
    private var contentHeight = 0
    private var viewportHeight = 0
    private var dragging = false

    /** Feed the container's current geometry. Called on every scroll and on every desktop layout change. */
    fun onScrollPositionChanged(scrollY: Int, contentHeight: Int, viewportHeight: Int) {
        this.scrollY = scrollY
        this.contentHeight = contentHeight
        this.viewportHeight = viewportHeight
        invalidate()
    }

    /** True when the content is longer than the viewport, so there is something to scroll. */
    fun isScrollable(): Boolean = contentHeight > viewportHeight && viewportHeight > 0

    /** Height of the drawn thumb, never shorter than the minimum that stays grabbable. */
    internal fun thumbHeightPx(): Int {
        if (!isScrollable()) {
            return 0
        }
        val proportional = height.toLong() * viewportHeight / contentHeight
        return max(minThumbHeightPx, min(height, proportional.toInt()))
    }

    /** Top of the drawn thumb for the current scroll position. */
    internal fun thumbTopPx(): Int {
        val travel = height - thumbHeightPx()
        if (travel <= 0 || !isScrollable()) {
            return 0
        }
        val maxScroll = contentHeight - viewportHeight
        return (travel.toLong() * scrollY / maxScroll).toInt().coerceIn(0, travel)
    }

    /**
     * Scroll position that puts the centre of the thumb under [touchY], clamped to the scrollable range.
     * Zero whenever there is nothing to scroll.
     */
    internal fun scrollOffsetForTouch(touchY: Float): Int {
        val travel = height - thumbHeightPx()
        if (travel <= 0 || !isScrollable()) {
            return 0
        }
        val maxScroll = contentHeight - viewportHeight
        val position = (touchY - thumbHeightPx() / 2f).coerceIn(0f, travel.toFloat())
        return (position / travel * maxScroll).roundToInt().coerceIn(0, maxScroll)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isScrollable()) {
            return
        }
        val left = (width - trackWidthPx) / 2f
        val top = thumbTopPx().toFloat()
        thumbRect.set(left, top, left + trackWidthPx, top + thumbHeightPx())
        val radius = trackWidthPx / 2f
        canvas.drawRoundRect(thumbRect, radius, radius, thumbPaint)
    }

    // The view is a scroll control, not a button: a tap on it has no meaning of its own, and performClick is
    // overridden below so accessibility services still see a clickable target.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isScrollable()) {
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginDrag(event)
            MotionEvent.ACTION_MOVE -> continueDrag(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
            else -> false
        }
    }

    override fun performClick(): Boolean = super.performClick()

    private fun beginDrag(event: MotionEvent): Boolean {
        // A touch away from the thumb belongs to whatever sits under this view - the rightmost column of
        // shortcuts (strategic section 7).
        val top = thumbTopPx()
        val inThumb = event.y >= top && event.y <= top + thumbHeightPx()
        if (!inThumb) {
            return false
        }
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun continueDrag(event: MotionEvent): Boolean {
        if (!dragging) {
            return false
        }
        onScrollRequested?.invoke(scrollOffsetForTouch(event.y))
        return true
    }

    private fun endDrag(): Boolean {
        if (!dragging) {
            return false
        }
        dragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
        performClick()
        return true
    }
}
