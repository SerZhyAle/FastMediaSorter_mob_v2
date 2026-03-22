package com.sza.fastmediasorter.ui.player.helpers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom View rendering a procedural wave + drifting particle animation as an audio background.
 *
 * Algorithm:
 *  - 5–12 sine-wave paths drawn per frame (count, stroke, amplitude, color randomized per session).
 *  - 15–55 drifting particles that bounce off view edges (count, size, speed, hue randomized).
 *  - Motion-blur trail effect via a semi-transparent black overlay drawn onto an
 *    off-screen Bitmap each frame, avoiding expensive post-processing blur filters.
 *
 * All visual parameters are re-randomized on each fresh [startAnimation] call so every
 * playback session looks distinct.
 *
 * Public lifecycle API mirrors [AudioBreathingBarsView]:
 *  - [startAnimation] — called when audio starts playing
 *  - [pauseAnimation] — called when audio is paused (retains last frame)
 *  - [stopAndReset]   — cancels animator, clears buffer, resets time
 */
class AudioWaveParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Must match the speed of the HTML canvas version: time += 0.003 per animation frame.
        // ValueAnimator fires ~60fps; 0.003 per tick gives ~0.18/s drift.
        private const val TIME_INCREMENT = 0.003f

        // Randomization ranges
        private const val WAVE_COUNT_MIN    = 5
        private const val WAVE_COUNT_MAX    = 12
        private const val STEP_PX_BASE      = 20f   // ±20 % → 16..24 px
        private const val STROKE_MIN        = 3f
        private const val STROKE_MAX        = 6f
        private const val AMPLITUDE_MIN     = 0.28f // fraction of view height
        private const val AMPLITUDE_MAX     = 0.48f
        private const val PARTICLE_MIN      = 15
        private const val PARTICLE_MAX      = 55
        private const val PARTICLE_R_MIN    = 1f    // px
        private const val PARTICLE_R_MAX    = 6f    // px
        private const val SPEED_MULT_MIN    = 0.5f
        private const val SPEED_MULT_MAX    = 1.5f
        private const val HUE_SPREAD_DEG    = 108f  // ±30 % of 360°
    }

    private var time = 0f

    // ── Randomized session parameters — re-rolled on each fresh startAnimation() ──

    /** Number of sine-wave paths to draw this session (5..12). */
    private var waveCount = 6

    /** Horizontal sampling step for wave path points, px (±20 % of 20 px). */
    private var stepPx = STEP_PX_BASE

    /** Stroke width for all wave lines, px (3..6). */
    private var waveStrokeWidth = 3f

    /** Base hue for wave palette (0..360); per-wave hue is offset by [waveHueStep]. */
    private var baseWaveHue = 210f

    /** Hue increment between adjacent waves, degrees (8..20). */
    private var waveHueStep = 12f

    /** Vertical amplitude of waves as a fraction of view height (28 %..48 %). */
    private var waveAmplitude = 0.38f

    /** Global speed multiplier for all particles (0.5..1.5×). */
    private var particleSpeedMult = 1.0f

    /** Center hue for particle palette (0..360); each particle is offset ±30° within the spread. */
    private var particleHueBase = 230f

    /** Actual particle count this session (15..55). */
    private var particleCountCurrent = 55

    // ──────────────────────────────────────────────────────────────────────────────

    private data class Particle(
        var x: Float,
        var y: Float,
        val radius: Float,
        var vx: Float,
        var vy: Float,
        val hue: Float
    )

    private val particles = ArrayList<Particle>(PARTICLE_MAX)

    // Off-screen Bitmap so the motion-blur trail accumulates across frames.
    // Without this, Android clears the Canvas on every onDraw call.
    private var offBitmap: Bitmap? = null
    private var offCanvas: Canvas? = null

    // rgba(10, 10, 10, 0.15) ≈ argb(38, 10, 10, 10) — semi-transparent black overlay
    private val fadeOverlayPaint = Paint().apply {
        color = Color.argb(38, 10, 10, 10)
        style = Paint.Style.FILL
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Full-opacity blit: copy off-screen buffer to the real canvas each frame.
    private val blitPaint = Paint()

    private val wavePath = Path()

    // Drives time increments and invalidation; actual animation value is unused.
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 10_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time += TIME_INCREMENT
            tick()
            invalidate()
        }
    }

    // ──────────────────── Lifecycle ────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w <= 0 || h <= 0) return
        offBitmap?.recycle()
        offBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        offCanvas = Canvas(offBitmap!!).also { it.drawColor(Color.BLACK) }
        initParticles(w, h)
    }

    /**
     * Re-rolls all randomized session parameters.
     * Called once at the start of each fresh playback session (not on resume-from-pause).
     */
    private fun randomizeParams() {
        waveCount         = Random.nextInt(WAVE_COUNT_MIN, WAVE_COUNT_MAX + 1)
        stepPx            = STEP_PX_BASE * (0.8f + Random.nextFloat() * 0.4f)   // ±20 %
        waveStrokeWidth   = STROKE_MIN + Random.nextFloat() * (STROKE_MAX - STROKE_MIN)
        baseWaveHue       = (Random.nextFloat() * 360f - HUE_SPREAD_DEG / 2f + 360f) % 360f
        waveHueStep       = 8f + Random.nextFloat() * 12f                        // 8..20°
        waveAmplitude     = AMPLITUDE_MIN + Random.nextFloat() * (AMPLITUDE_MAX - AMPLITUDE_MIN)
        particleSpeedMult = SPEED_MULT_MIN + Random.nextFloat() * (SPEED_MULT_MAX - SPEED_MULT_MIN)
        particleHueBase   = Random.nextFloat() * 360f
        particleCountCurrent = Random.nextInt(PARTICLE_MIN, PARTICLE_MAX + 1)
    }

    private fun initParticles(w: Int, h: Int) {
        particles.clear()
        repeat(particleCountCurrent) {
            particles += Particle(
                x      = Random.nextFloat() * w,
                y      = Random.nextFloat() * h,
                radius = PARTICLE_R_MIN + Random.nextFloat() * (PARTICLE_R_MAX - PARTICLE_R_MIN),
                vx     = (Random.nextFloat() - 0.5f) * 0.6f * particleSpeedMult,
                vy     = (Random.nextFloat() - 0.5f) * 0.6f * particleSpeedMult,
                hue    = (particleHueBase + (Random.nextFloat() - 0.5f) * HUE_SPREAD_DEG + 360f) % 360f
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        offBitmap?.let { canvas.drawBitmap(it, 0f, 0f, blitPaint) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
        offBitmap?.recycle()
        offBitmap = null
        offCanvas = null
    }

    // ──────────────────── Public API ────────────────────

    fun startAnimation() {
        when {
            animator.isPaused -> animator.resume()
            !animator.isRunning -> {
                randomizeParams()
                val w = width.takeIf  { it > 0 } ?: return
                val h = height.takeIf { it > 0 } ?: return
                initParticles(w, h)
                wavePaint.strokeWidth = waveStrokeWidth
                animator.start()
            }
            // already running — no-op
        }
    }

    fun pauseAnimation() {
        if (animator.isRunning && !animator.isPaused) animator.pause()
    }

    fun stopAndReset() {
        animator.cancel()
        time = 0f
        offCanvas?.drawColor(Color.BLACK)
        invalidate()
    }

    // ──────────────────── Drawing ────────────────────

    /** Updates the off-screen buffer (called each animation tick). */
    private fun tick() {
        val oc = offCanvas ?: return
        val bm = offBitmap ?: return
        val w = bm.width.toFloat()
        val h = bm.height.toFloat()

        // Semi-transparent overlay creates the motion-blur trail
        oc.drawRect(0f, 0f, w, h, fadeOverlayPaint)

        // Sine-wave paths — count, step, stroke, color and amplitude vary per session
        for (j in 0 until waveCount) {
            val phaseShift = j * 15f
            wavePath.rewind()
            var x = 0f
            var first = true
            while (x <= w) {
                val y = h / 2f +
                    sin((x * 0.003f + time + phaseShift).toDouble()).toFloat() *
                    h * waveAmplitude *
                    sin((time * 0.4f + j * 0.2f).toDouble()).toFloat()
                if (first) {
                    wavePath.moveTo(x, y)
                    first = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += stepPx
            }
            wavePaint.color = hslToArgb((baseWaveHue + j * waveHueStep) % 360f, 0.80f, 0.65f, 0.40f)
            oc.drawPath(wavePath, wavePaint)
        }

        // Drifting particles with edge bounce — radius, speed, hue vary per session
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            if (p.x < 0f || p.x > w) p.vx = -p.vx
            if (p.y < 0f || p.y > h) p.vy = -p.vy
            particlePaint.color = hslToArgb(p.hue, 0.90f, 0.70f, 0.70f)
            oc.drawCircle(p.x, p.y, p.radius, particlePaint)
        }
    }

    // ──────────────────── Color ────────────────────

    /** Converts HSL + alpha to an ARGB integer. h: [0,360], s/l/a: [0,1]. */
    private fun hslToArgb(h: Float, s: Float, l: Float, a: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f  -> Triple(c + m, x + m, m)
            h < 120f -> Triple(x + m, c + m, m)
            h < 180f -> Triple(m,     c + m, x + m)
            h < 240f -> Triple(m,     x + m, c + m)
            h < 300f -> Triple(x + m, m,     c + m)
            else     -> Triple(c + m, m,     x + m)
        }
        return Color.argb(
            (a * 255f).toInt(),
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }
}
