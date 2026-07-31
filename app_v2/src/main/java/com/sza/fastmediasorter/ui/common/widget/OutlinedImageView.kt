package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.sza.fastmediasorter.R

/**
 * ImageView that paints a contour around its drawable's silhouette, so an icon stays legible over a
 * background it knows nothing about - a bright photo, a dark one, or a busy one (S1173 launcher cells).
 * A plain shadow is not enough: over a bright wallpaper it reads as smudge rather than as an edge.
 *
 * The silhouette comes from the drawable's own alpha, which is why this works for a colour app icon
 * just as well as for a monochrome vector - the widget never needs to know the glyph's shape.
 *
 * The contour is drawn inside the view's bounds, so a consumer must leave padding of at least the
 * outline width or the outermost pass is clipped at the edge. A zero width switches the contour off.
 */
class OutlinedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val outlineWidthPx: Float
    private val silhouetteFilter: PorterDuffColorFilter
    private var drawingOutline = false

    init {
        var color = ContextCompat.getColor(context, R.color.outline_text_stroke)
        var widthPx = resources.getDimension(R.dimen.outline_text_stroke_width)
        context.obtainStyledAttributes(attrs, R.styleable.OutlinedImageView, defStyleAttr, 0).use { ta ->
            color = ta.getColor(R.styleable.OutlinedImageView_oiv_outlineColor, color)
            widthPx = ta.getDimension(R.styleable.OutlinedImageView_oiv_outlineWidth, widthPx)
        }
        outlineWidthPx = widthPx
        // SRC_IN keeps the drawable's alpha and replaces every colour with the contour colour - that is
        // what turns an arbitrary icon into a silhouette without inspecting its shape.
        silhouetteFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    // Swapping the drawable's colour filter asks the host view to redraw. Swallow that while the contour
    // passes run, or each frame schedules the next one.
    override fun invalidate() {
        if (drawingOutline) return
        super.invalidate()
    }

    override fun invalidateDrawable(drawable: Drawable) {
        if (drawingOutline) return
        super.invalidateDrawable(drawable)
    }

    /** A zero width is a consumer switching the contour off; a zero size means nothing to outline yet. */
    private fun canDrawContour(): Boolean = outlineWidthPx > 0f && width > 0 && height > 0

    override fun onDraw(canvas: Canvas) {
        val icon = drawable
        if (icon == null || !canDrawContour()) {
            super.onDraw(canvas)
            return
        }
        drawingOutline = true
        // Restored before returning, so no other view can observe the borrowed drawable tinted: the swap
        // and the passes all happen inside this one synchronous draw on the UI thread.
        val callerFilter = icon.colorFilter
        icon.colorFilter = silhouetteFilter
        // Translating the canvas and letting super draw keeps ImageView's own scale and padding matrix
        // authoritative; reproducing that placement here would drift the moment scaleType changes.
        OFFSETS.forEach { (dx, dy) ->
            canvas.save()
            canvas.translate(dx * outlineWidthPx, dy * outlineWidthPx)
            super.onDraw(canvas)
            canvas.restore()
        }
        icon.colorFilter = callerFilter
        drawingOutline = false
        super.onDraw(canvas)
    }

    private companion object {
        /** Eight neighbours approximate a dilation; four would leave visible gaps at the corners. */
        val OFFSETS = arrayOf(
            -1f to -1f,
            0f to -1f,
            1f to -1f,
            -1f to 0f,
            1f to 0f,
            -1f to 1f,
            0f to 1f,
            1f to 1f,
        )
    }
}
