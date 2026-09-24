package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.animation.PathInterpolator
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.sensors.CompassReading
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fullscreen black overlay view with Wear-dimming inspired wake gestures (S3203, S3097, S3200).
 *
 * Single tap does not exit; it draws the shared tap response (S3370, sized up by S3475): an expanding
 * ring plus four tapered sparks on the cardinal points that fly out of the tap point and fade long
 * before the ring does.
 * Double tap or long press wakes / exits the dim mode, invoking [onExit].
 * Hardware Back or Escape key also exits.
 */
class DimOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Callback invoked when a double-tap, long-press, or Back key dismisses the dim overlay. */
    var onExit: (() -> Unit)? = null

    /** Invoked on any touch while dimmed; the clock overlay resets its idle fade on it (S3361). */
    var onUserActivity: (() -> Unit)? = null

    /**
     * S3370: heading at tap time, wired by the host to a cached compass reading. Null, or a reading
     * whose accuracy is [SensorAccuracy.UNRELIABLE], sends the spark pair white on a random azimuth.
     */
    var headingLookup: (() -> CompassReading?)? = null

    private var tapX = 0f
    private var tapY = 0f
    private var animStartTime = 0L
    private var sparkAzimuthDegrees = 0f

    @get:VisibleForTesting
    var isAnimating = false
        private set

    @get:VisibleForTesting
    var animationProgress = 1f
        private set

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * TAP_MARK_STROKE_DP
        color = Color.WHITE
    }

    // One path and one paint, reused across the frames a spark lives: a fresh pair per frame would
    // allocate on every animation tick of a 2.8 s draw.
    private val sparkPath = Path()
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    @VisibleForTesting
    val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            startRippleAnimation(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onExit?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onExit?.invoke()
        }
    }

    @VisibleForTesting
    val gestureDetector = GestureDetector(context, gestureListener)

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        fitsSystemWindows = false
        contentDescription = context.getString(R.string.broadcast_control_blank_screen_cd)
    }

    fun startRippleAnimation(x: Float, y: Float) {
        tapX = x
        tapY = y
        animStartTime = SystemClock.uptimeMillis()
        animationProgress = 0f
        captureSparkAzimuth()
        isAnimating = true
        postInvalidateOnAnimation()
    }

    /**
     * S3370 ADR-2: azimuth and color are decided once, at the tap that starts the animation.
     * S3475: the compass reports the heading of the device's natural top edge, so a turned display
     * adds its own rotation - without it the landscape sparks pointed a quarter turn off north.
     */
    private fun captureSparkAzimuth() {
        val reading = headingLookup?.invoke()
        sparkAzimuthDegrees = if (reading != null && reading.accuracy != SensorAccuracy.UNRELIABLE) {
            screenAzimuth(reading.azimuthDegrees, displayRotationDegrees())
        } else {
            Random.nextFloat() * FULL_CIRCLE_DEGREES
        }
        Timber.d("S3475: spark azimuth=$sparkAzimuthDegrees heading=${reading?.azimuthDegrees}")
    }

    private fun displayRotationDegrees(): Int = when (display?.rotation) {
        Surface.ROTATION_90 -> QUARTER_TURN_DEGREES
        Surface.ROTATION_180 -> HALF_TURN_DEGREES
        Surface.ROTATION_270 -> THREE_QUARTER_TURN_DEGREES
        else -> 0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onUserActivity?.invoke()
        gestureDetector.onTouchEvent(event)
        // Always consume all touch events while the dim overlay is displayed
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onExit?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isAnimating) {
            val elapsed = SystemClock.uptimeMillis() - animStartTime
            if (elapsed >= TAP_MARK_DURATION_MS) {
                isAnimating = false
                animationProgress = 1f
            } else {
                val linearFraction = elapsed.toFloat() / TAP_MARK_DURATION_MS
                val progress = TAP_MARK_INTERPOLATOR.getInterpolation(linearFraction)
                animationProgress = progress
                val density = resources.displayMetrics.density
                val startRadius = density * TAP_MARK_START_RADIUS_DP
                val endRadius = density * TAP_MARK_END_RADIUS_DP
                val radius = startRadius + (endRadius - startRadius) * progress
                val alpha = ((1f - progress) * MAX_ALPHA_FLOAT).toInt().coerceIn(0, MAX_ALPHA_INT)
                ripplePaint.alpha = alpha
                canvas.drawCircle(tapX, tapY, radius, ripplePaint)
                drawSparkPair(canvas, elapsed, density)
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawSparkPair(canvas: Canvas, elapsedMillis: Long, density: Float) {
        if (elapsedMillis >= SPARK_DURATION_MS) return
        val fraction = elapsedMillis.toFloat() / SPARK_DURATION_MS
        val progress = TAP_MARK_INTERPOLATOR.getInterpolation(fraction)
        val alpha = ((1f - progress) * MAX_ALPHA_FLOAT).toInt().coerceIn(0, MAX_ALPHA_INT)
        // S3475: one spark per cardinal point - north red, south blue, east and west white - so the
        // burst reads as the compass rose itself rather than a single needle.
        SPARK_CARDINAL_COLORS.forEachIndexed { index, color ->
            val azimuth = (sparkAzimuthDegrees + index * QUARTER_TURN_DEGREES) % FULL_CIRCLE_DEGREES
            drawSparkStripe(canvas, azimuth, progress, density, alpha, color)
        }
    }

    private fun drawSparkStripe(
        canvas: Canvas,
        azimuthDegrees: Float,
        progress: Float,
        density: Float,
        alpha: Int,
        color: Int,
    ) {
        val (dirX, dirY) = sparkNorthDirectionComponents(azimuthDegrees)
        val normalX = -dirY
        val normalY = dirX
        val baseOffset = density * SPARK_TRAVEL_DP * progress
        val length = density * SPARK_LENGTH_DP
        val peakHalfWidth = density * SPARK_PEAK_WIDTH_DP * HALF
        sparkPath.rewind()
        for (i in 0..SPARK_PROFILE_STEPS) {
            val t = i.toFloat() / SPARK_PROFILE_STEPS
            val along = baseOffset + length * t
            val halfWidth = peakHalfWidth * sparkWidthProfile(t)
            val centerX = tapX + dirX * along
            val centerY = tapY + dirY * along
            val pointX = centerX + normalX * halfWidth
            val pointY = centerY + normalY * halfWidth
            if (i == 0) sparkPath.moveTo(pointX, pointY) else sparkPath.lineTo(pointX, pointY)
        }
        for (i in SPARK_PROFILE_STEPS downTo 0) {
            val t = i.toFloat() / SPARK_PROFILE_STEPS
            val along = baseOffset + length * t
            val halfWidth = peakHalfWidth * sparkWidthProfile(t)
            sparkPath.lineTo(
                tapX + dirX * along - normalX * halfWidth,
                tapY + dirY * along - normalY * halfWidth,
            )
        }
        sparkPath.close()
        sparkPaint.color = color
        sparkPaint.alpha = alpha
        canvas.drawPath(sparkPath, sparkPaint)
    }

    override fun onDetachedFromWindow() {
        isAnimating = false
        super.onDetachedFromWindow()
    }

    internal companion object {

        /**
         * S3370: screen direction of geographic north for the device azimuth, in screen coordinates
         * (x right, y down). Pure so the spark tests run plain JUnit over it.
         */
        internal fun sparkNorthDirectionComponents(azimuthDegrees: Float): Pair<Float, Float> {
            val radians = Math.toRadians(azimuthDegrees.toDouble())
            return -sin(radians).toFloat() to -cos(radians).toFloat()
        }

        /** S3370: width along a spark stripe, 0 at both ends and peaking mid-stroke. */
        internal fun sparkWidthProfile(t: Float): Float = sin(PI * t).toFloat()

        /** S3475: the device heading as seen on a display turned by [displayRotationDegrees]. */
        internal fun screenAzimuth(deviceAzimuthDegrees: Float, displayRotationDegrees: Int): Float =
            (deviceAzimuthDegrees + displayRotationDegrees) % FULL_CIRCLE_DEGREES

        private const val MAX_ALPHA_FLOAT = 255f
        private const val MAX_ALPHA_INT = 255
        private const val TAP_MARK_DURATION_MS = 2800L
        private const val TAP_MARK_START_RADIUS_DP = 6f
        private const val TAP_MARK_END_RADIUS_DP = 192f
        private const val TAP_MARK_STROKE_DP = 2f
        private const val SPARK_DURATION_MS = 700L
        private const val SPARK_LENGTH_DP = 120f
        private const val SPARK_PEAK_WIDTH_DP = 6f
        private const val SPARK_TRAVEL_DP = 40f
        private const val SPARK_PROFILE_STEPS = 12
        private const val FULL_CIRCLE_DEGREES = 360f
        private const val QUARTER_TURN_DEGREES = 90
        private const val HALF_TURN_DEGREES = 180
        private const val THREE_QUARTER_TURN_DEGREES = 270
        private const val HALF = 0.5f
        private const val SPARK_NORTH_COLOR = 0xFFE53935.toInt()
        private const val SPARK_SOUTH_COLOR = 0xFF448AFF.toInt()

        /** Clockwise-by-index offsets of 90 degrees from north: north, west, south, east. */
        private val SPARK_CARDINAL_COLORS = listOf(SPARK_NORTH_COLOR, Color.WHITE, SPARK_SOUTH_COLOR, Color.WHITE)
        private val TAP_MARK_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}
