package com.sza.fastmediasorter.ui.tourist.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.domain.model.sensors.AttitudeReading
import com.google.android.material.R as MaterialR

/**
 * S4560: the gyroscope tile's picture - a phone-shaped box that turns with the phone.
 *
 * Fed at sensor rate (about 50 Hz), so [onDraw] allocates nothing: the projection buffers, the path and
 * the paints are fields. The screen face and the top edge have their own colours so the box reads as a
 * phone with a known up even when only one face shows.
 */
class AttitudeBoxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val projector = AttitudeBoxProjector()
    private val points = FloatArray(AttitudeBoxProjector.VERTEX_COUNT * 2)
    private val shades = FloatArray(AttitudeBoxProjector.FACE_COUNT)
    private val facePath = Path()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = resources.displayMetrics.density * EDGE_WIDTH_DP
    }

    private val screenColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val topColor = MaterialColors.getColor(this, MaterialR.attr.colorTertiary)
    private val bodyColor = MaterialColors.getColor(this, MaterialR.attr.colorOnSurfaceVariant)
    private val shadowColor = MaterialColors.getColor(this, MaterialR.attr.colorSurface)

    private var qx = 0f
    private var qy = 0f
    private var qz = 0f
    private var qw = 1f

    init {
        edgePaint.color = MaterialColors.getColor(this, MaterialR.attr.colorOnSurface)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A null reading rests the box and makes the next reading the new "facing the user" pose. */
    fun setAttitude(reading: AttitudeReading?) {
        if (reading == null) {
            qx = 0f
            qy = 0f
            qz = 0f
            qw = 1f
            projector.resetReference()
        } else {
            qx = reading.x
            qy = reading.y
            qz = reading.z
            qw = reading.w
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val usable = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom)
        if (usable <= 0) return
        projector.project(qx, qy, qz, qw, points, shades)
        val scale = usable / 2f / AttitudeBoxProjector.MAX_EXTENT
        val centerX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val centerY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        for (face in 0 until AttitudeBoxProjector.FACE_COUNT) {
            val shade = shades[face]
            if (shade <= 0f) continue
            buildFacePath(face, centerX, centerY, scale)
            val base = when (face) {
                AttitudeBoxProjector.FACE_SCREEN -> screenColor
                AttitudeBoxProjector.FACE_TOP -> topColor
                else -> bodyColor
            }
            fillPaint.color = ColorUtils.blendARGB(shadowColor, base, MIN_LIGHT + (1f - MIN_LIGHT) * shade)
            canvas.drawPath(facePath, fillPaint)
            canvas.drawPath(facePath, edgePaint)
        }
    }

    /** Model y points up, canvas y points down. */
    private fun buildFacePath(face: Int, centerX: Float, centerY: Float, scale: Float) {
        facePath.rewind()
        val base = face * AttitudeBoxProjector.CORNERS_PER_FACE
        for (k in 0 until AttitudeBoxProjector.CORNERS_PER_FACE) {
            val corner = AttitudeBoxProjector.FACES[base + k]
            val x = centerX + points[corner * 2] * scale
            val y = centerY - points[corner * 2 + 1] * scale
            if (k == 0) facePath.moveTo(x, y) else facePath.lineTo(x, y)
        }
        facePath.close()
    }

    private companion object {
        const val EDGE_WIDTH_DP = 1.5f
        const val MIN_LIGHT = 0.45f
    }
}
