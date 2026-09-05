package com.sza.fastmediasorter.ui.player.helpers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.sza.fastmediasorter.core.util.AnimationIntent
import com.sza.fastmediasorter.core.util.AnimationPolicy
import android.view.animation.LinearInterpolator
import timber.log.Timber
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom View that renders 15 "breathing" equalizer bars animated via sine waves.
 * Each bar has an individual phase offset and a slightly different pastel hue to
 * create a natural wave effect with a soft colour gradient across the set.
 *
 * Animation lifecycle:
 *  - [startAnimation] - launched when audio starts playing
 *  - [pauseAnimation]  - paused when audio is paused (retains current frame)
 *  - [stopAndReset]    - fully resets to the minimum height state
 */
class AudioBreathingBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class RenderMode { BARS, RINGS }

    /** Current render mode - BARS (equalizer) or RINGS (pulsing concentric circles for AVD_PULSE). */
    var renderMode: RenderMode = RenderMode.BARS

    companion object {
        private const val BAR_COUNT = 15
        private const val ANIMATION_DURATION_MS = 5000L
        private const val HUE_CYCLE_DURATION_MS = 45_000L  // full-spectrum colour drift period
        private const val MIN_HEIGHT_FRACTION = 0.15f   // fraction of view height
        private const val MAX_HEIGHT_FRACTION = 0.85f
        private const val BAR_CORNER_RADIUS_DP = 4f
        private const val INTER_BAR_GAP_FRACTION = 0.15f // gap = barWidth * fraction
        private const val RING_COUNT = 8
        private const val RING_STROKE_WIDTH_DP = 8f
    }

    /**
     * Per-bar base hue offsets (0..60°). Combined with the animated [hueShift] they
     * produce a slowly drifting palette: purple → green → yellow → back.
     */
    private val baseHues: FloatArray = FloatArray(BAR_COUNT) { index ->
        index * (60f / (BAR_COUNT - 1))
    }

    /** Current global hue offset (0..360°), updated by [hueAnimator]. Starts at purple 255°. */
    private var hueShift = 255f

    /** Reusable HSV array to avoid per-frame allocations inside onDraw. */
    private val hsv = floatArrayOf(255f, 0.42f, 0.96f)

    /** Per-ring hues, randomized on each startAnimation() call in RINGS mode. */
    private val ringHues: FloatArray = FloatArray(RING_COUNT) { Random.nextFloat() * 360f }

    /** Reusable HSV for rings - vivid saturation/value, separate from pastel bars. */
    private val ringHsv = floatArrayOf(0f, 0.90f, 1.0f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rect = RectF()

    /** Phase offset per bar in radians (spreads the wave across all bars) */
    private val phaseOffsets: FloatArray = FloatArray(BAR_COUNT) { index ->
        (2.0 * Math.PI * index / BAR_COUNT).toFloat()
    }

    /** Animated progress: 0..1 (one full sine cycle) */
    private var animProgress = 0f

    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ANIMATION_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            animProgress = anim.animatedValue as Float
            invalidate()
        }
    }

    /** Slowly shifts [hueShift] through the full 360° colour wheel over [HUE_CYCLE_DURATION_MS]. */
    private val hueAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = HUE_CYCLE_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            // Start from purple (255°) and drift forward through green, yellow, etc.
            hueShift = (255f + anim.animatedValue as Float) % 360f
        }
    }

    private val cornerRadiusPx: Float by lazy {
        BAR_CORNER_RADIUS_DP * resources.displayMetrics.density
    }

    private val ringStrokeWidthPx: Float by lazy {
        RING_STROKE_WIDTH_DP * resources.displayMetrics.density
    }

    // ────────────────────────── Public API ──────────────────────────

    // Fires on the settings collector's thread, so the work is posted to the view's own thread.
    private val policyListener: () -> Unit = { post { refreshPolicy() } }

    /** True while the policy, not the host, is what is holding the animation still. */
    private var frozenByPolicy = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AnimationPolicy.addLevelListener(policyListener)
    }

    /**
     * S2536: this is the alternate skin of the same visualizer, so it follows the same rule - it keeps
     * moving under the user's cosmetic switch and freezes only in the power-saving level. Leaving it
     * out would make strategic criterion 3 true on one player skin and false on the other.
     */
    fun refreshPolicy() {
        if (AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT)) {
            if (frozenByPolicy) startAnimation()
        } else if (!frozenByPolicy) {
            frozenByPolicy = true
            // Pause, never cancel: the last computed frame stays on screen, so a frozen visualizer
            // reads as held rather than as blank.
            pauseAnimation()
        }
    }

    fun startAnimation() {
        if (!AnimationPolicy.mayAnimate(AnimationIntent.AMBIENT)) {
            frozenByPolicy = true
            pauseAnimation()
            return
        }
        frozenByPolicy = false
        when {
            animator.isPaused -> {
                Timber.d("AudioBreathingBarsView: startAnimation() - resume from pause")
                animator.resume()
                hueAnimator.resume()
            }
            !animator.isRunning -> {
                Timber.d("AudioBreathingBarsView: startAnimation() - fresh start")
                if (renderMode == RenderMode.RINGS) regenerateRingColors()
                animator.start()
                hueAnimator.start()
            }
            // already running - no-op
        }
    }

    fun pauseAnimation() {
        if (animator.isRunning && !animator.isPaused) {
            Timber.d("AudioBreathingBarsView: pauseAnimation()")
            animator.pause()
            hueAnimator.pause()
        }
    }

    fun stopAndReset() {
        Timber.d("AudioBreathingBarsView: stopAndReset()")
        animator.cancel()
        hueAnimator.cancel()
        animProgress = 0f
        hueShift = 255f
        invalidate()
    }

    // ────────────────────────── Drawing ──────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (renderMode == RenderMode.BARS) drawBars(canvas) else drawRings(canvas)
    }

    private fun drawBars(canvas: Canvas) {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        // Divide total width evenly: 70% to bar, 30% gap
        val columnWidth = viewWidth / BAR_COUNT
        val barW = columnWidth * (1f - INTER_BAR_GAP_FRACTION)
        val gap = columnWidth * INTER_BAR_GAP_FRACTION

        for (i in 0 until BAR_COUNT) {
            val phase = phaseOffsets[i]
            // sine oscillates −1..1, map to MIN..MAX height fraction
            val sinValue = sin((animProgress * 2.0 * Math.PI + phase).toFloat()).toFloat()
            val heightFraction = MIN_HEIGHT_FRACTION + (sinValue + 1f) / 2f * (MAX_HEIGHT_FRACTION - MIN_HEIGHT_FRACTION)
            val barHeight = viewHeight * heightFraction

            val left = i * (barW + gap)
            val top = viewHeight - barHeight
            val right = left + barW
            val bottom = viewHeight

            rect.set(left, top, right, bottom)
            hsv[0] = (hueShift + baseHues[i]) % 360f
            paint.color = Color.HSVToColor(hsv)
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }
    }

    /**
     * Ripple effect for AVD_PULSE mode: each ring is born at the center as a point,
     * continuously expands until it leaves the screen, then repeats.
     * Rings are staggered evenly so a new one appears every (duration / RING_COUNT) ms.
     * Alpha fades from opaque to transparent as radius grows - the ring vanishes off-screen.
     * Colors are randomized per ring on each startAnimation() call.
     */
    private fun drawRings(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        // Diagonal from center to corner - guarantees ring fully exits the screen
        val maxRadius = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat() * 1.08f

        paint.style = Paint.Style.STROKE

        for (i in 0 until RING_COUNT) {
            // Each ring is offset by 1/RING_COUNT of the cycle - continuous staggered stream
            val progress = (animProgress + i.toFloat() / RING_COUNT) % 1f  // 0..1
            val radius = maxRadius * progress
            // Fade: full opacity at center, transparent at edge
            val alpha = ((1f - progress).coerceIn(0f, 1f) * 230).toInt()
            // Stroke thins as ring expands for a natural ripple feel
            paint.strokeWidth = ringStrokeWidthPx * (1f - progress * 0.65f).coerceAtLeast(0.15f)
            ringHsv[0] = ringHues[i]
            paint.color = Color.HSVToColor(alpha, ringHsv)
            canvas.drawCircle(cx, cy, radius, paint)
        }

        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
    }

    private fun regenerateRingColors() {
        for (i in ringHues.indices) ringHues[i] = Random.nextFloat() * 360f
    }

    override fun onDetachedFromWindow() {
        AnimationPolicy.removeLevelListener(policyListener)
        super.onDetachedFromWindow()
        animator.cancel()
        hueAnimator.cancel()
    }
}
