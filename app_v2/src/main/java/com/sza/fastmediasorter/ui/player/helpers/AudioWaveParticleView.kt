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
 *  - 6 sine-wave paths drawn per frame (superimposed frequencies produce an organic
 *    non-repeating interference pattern — same formula as the HTML5 Canvas version).
 *  - 55 drifting particles that bounce off view edges.
 *  - Motion-blur trail effect via a semi-transparent black overlay drawn onto an
 *    off-screen Bitmap each frame, avoiding expensive post-processing blur filters.
 *
 * Color palette: blue → violet range (hue 210–270), matching the app's primary/secondary theme.
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
        private const val PARTICLE_COUNT = 55
        private const val WAVE_COUNT = 6

        // Must match the speed of the HTML canvas version: time += 0.003 per animation frame.
        // ValueAnimator fires ~60fps; 0.003 per tick gives ~0.18/s drift.
        private const val TIME_INCREMENT = 0.003f
        private const val STEP_PX = 20f
    }

    private var time = 0f

    private data class Particle(
        var x: Float,
        var y: Float,
        val radius: Float,
        var vx: Float,
        var vy: Float,
        val hue: Float
    )

    private val particles = ArrayList<Particle>(PARTICLE_COUNT)

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

    private fun initParticles(w: Int, h: Int) {
        particles.clear()
        repeat(PARTICLE_COUNT) {
            particles += Particle(
                x = Random.nextFloat() * w,
                y = Random.nextFloat() * h,
                radius = Random.nextFloat() * 2f + 0.5f,
                vx = (Random.nextFloat() - 0.5f) * 0.6f,
                vy = (Random.nextFloat() - 0.5f) * 0.6f,
                hue = Random.nextFloat() * 60f + 200f  // blue-violet: 200..260
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
            !animator.isRunning -> animator.start()
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

        // 6 sine-wave paths with different phase offsets
        for (j in 0 until WAVE_COUNT) {
            val phaseShift = j * 15f
            wavePath.rewind()
            var x = 0f
            var first = true
            while (x <= w) {
                // Superimposed sine waves: same formula as the HTML canvas version
                val y = h / 2f +
                    sin((x * 0.003f + time + phaseShift).toDouble()).toFloat() *
                    h * 0.38f *
                    sin((time * 0.4f + j * 0.2f).toDouble()).toFloat()
                if (first) {
                    wavePath.moveTo(x, y)
                    first = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += STEP_PX
            }
            // hsla(210 + j*12, 80%, 65%, 0.4) — blue-violet gradient across 6 waves
            wavePaint.color = hslToArgb(210f + j * 12f, 0.80f, 0.65f, 0.40f)
            oc.drawPath(wavePath, wavePaint)
        }

        // Drifting particles with edge bounce
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            if (p.x < 0f || p.x > w) p.vx = -p.vx
            if (p.y < 0f || p.y > h) p.vy = -p.vy
            // hsla(hue, 90%, 70%, 0.7)
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
