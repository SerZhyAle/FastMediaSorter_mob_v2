package com.sza.fastmediasorter.ui.player.helpers

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
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
 *  - [startAnimation] - called when audio starts playing
 *  - [pauseAnimation] - called when audio is paused (retains last frame)
 *  - [stopAndReset]   - cancels animator, clears buffer, resets time
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

        // Randomization ranges - normal devices
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
        private const val STARTUP_RAMP_FRAMES = 36
        private const val WAVE_LANE_SPACING_FRACTION = 0.038f
        private const val PARTICLE_DIRECTIONAL_BIAS = 0.42f
        private const val PARTICLE_RANDOM_SPREAD = 0.28f
        private const val COUNTER_DRIFT_CHANCE = 0.18f

        // Reduced limits for low-RAM / weak devices
        private const val WAVE_COUNT_MIN_LOW    = 3
        private const val WAVE_COUNT_MAX_LOW    = 6
        private const val PARTICLE_MIN_LOW      = 6
        private const val PARTICLE_MAX_LOW      = 18

        // S1277: the look is accumulated, not drawn in one pass - each tick lays a translucent
        // overlay and draws over it, and the first STARTUP_RAMP_FRAMES ticks ramp amplitude and
        // alpha up from 35 %. The static path runs that ramp to completion so a device with
        // animations off gets the frame the animator would have reached, not a near-empty buffer.
        private const val STATIC_FRAME_PASSES = STARTUP_RAMP_FRAMES

        // Default when the animator scale cannot be read; matches the platform default.
        private const val ANIMATOR_SCALE_DEFAULT = 1f
    }

    /**
     * True if [startAnimation] was called while the View had no size yet (layout pending).
     * The deferred start fires in [onSizeChanged] once real dimensions are known.
     */
    private var pendingStart = false

    /** True if ActivityManager reports this as a low-RAM device. Set once in init{}. */
    private val isLowRam: Boolean

    init {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        isLowRam = am?.isLowRamDevice == true
        if (isLowRam) Timber.d("AudioWaveParticleView: low-RAM device detected - reduced object counts")
    }

    private var time = 0f
    private var startupFrameCount = 0

    // ── Randomized session parameters - re-rolled on each fresh startAnimation() ──

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

    /** Randomized flow direction for the full playback session in degrees. */
    private var waveDirectionAngleDeg = 0f

    /** Cached unit vector for the randomized session direction. */
    private var waveDirX = 1f
    private var waveDirY = 0f

    /** Perpendicular unit vector used for wave displacement and band spacing. */
    private var waveNormalX = 0f
    private var waveNormalY = 1f

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

    // rgba(10, 10, 10, 0.15) ≈ argb(38, 10, 10, 10) - semi-transparent black overlay
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
        // Deferred start: startAnimation() was called before layout completed
        if (pendingStart) {
            pendingStart = false
            Timber.d("AudioWaveParticleView: onSizeChanged - firing deferred startAnimation")
            wavePaint.strokeWidth = waveStrokeWidth
            animator.start()
        }
        // S1277: with system animations off the animator ends without ever firing its update
        // listener, so nothing would repaint the buffer this method just blacked out and the view
        // stays pure black for the rest of the session. Draw the frame here instead.
        if (animatorsDisabled()) renderStaticFrame()
    }

    /**
     * True when the user has turned system animations off, in which case a [ValueAnimator] ends
     * immediately and never delivers an update callback.
     *
     * [ValueAnimator.areAnimatorsEnabled] is the official signal but arrived in API 26, and the
     * legacy flavor builds against a lower minSdk - below that the same state is the animator
     * duration scale this ticket measured.
     */
    private fun animatorsDisabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                ANIMATOR_SCALE_DEFAULT
            ) == 0f
        }

    /** Builds one complete frame without the animator, then shows it. See [STATIC_FRAME_PASSES]. */
    private fun renderStaticFrame() {
        wavePaint.strokeWidth = waveStrokeWidth
        repeat(STATIC_FRAME_PASSES) {
            time += TIME_INCREMENT
            tick()
        }
        invalidate()
    }

    /**
     * Re-rolls all randomized session parameters.
     * Called once at the start of each fresh playback session (not on resume-from-pause).
     */
    private fun randomizeParams() {
        val waveMin = if (isLowRam) WAVE_COUNT_MIN_LOW else WAVE_COUNT_MIN
        val waveMax = if (isLowRam) WAVE_COUNT_MAX_LOW else WAVE_COUNT_MAX
        val pMin    = if (isLowRam) PARTICLE_MIN_LOW   else PARTICLE_MIN
        val pMax    = if (isLowRam) PARTICLE_MAX_LOW   else PARTICLE_MAX

        waveCount         = Random.nextInt(waveMin, waveMax + 1)
        stepPx            = STEP_PX_BASE * (0.8f + Random.nextFloat() * 0.4f)   // ±20 %
        waveStrokeWidth   = STROKE_MIN + Random.nextFloat() * (STROKE_MAX - STROKE_MIN)
        baseWaveHue       = (Random.nextFloat() * 360f - HUE_SPREAD_DEG / 2f + 360f) % 360f
        waveHueStep       = 8f + Random.nextFloat() * 12f                        // 8..20°
        waveAmplitude     = AMPLITUDE_MIN + Random.nextFloat() * (AMPLITUDE_MAX - AMPLITUDE_MIN)
        particleSpeedMult = SPEED_MULT_MIN + Random.nextFloat() * (SPEED_MULT_MAX - SPEED_MULT_MIN)
        particleHueBase   = Random.nextFloat() * 360f
        particleCountCurrent = Random.nextInt(pMin, pMax + 1)
        waveDirectionAngleDeg = Random.nextFloat() * 360f

        val angleRad = Math.toRadians(waveDirectionAngleDeg.toDouble())
        waveDirX = cos(angleRad).toFloat()
        waveDirY = sin(angleRad).toFloat()
        waveNormalX = -waveDirY
        waveNormalY = waveDirX
    }

    private fun initParticles(w: Int, h: Int) {
        particles.clear()
        repeat(particleCountCurrent) {
            val directionalSpeed = (0.12f + Random.nextFloat() * PARTICLE_DIRECTIONAL_BIAS) * particleSpeedMult
            val driftSign = if (Random.nextFloat() < COUNTER_DRIFT_CHANCE) -0.35f else 1f
            val directionalVx = waveDirX * directionalSpeed * driftSign
            val directionalVy = waveDirY * directionalSpeed * driftSign
            particles += Particle(
                x      = Random.nextFloat() * w,
                y      = Random.nextFloat() * h,
                radius = PARTICLE_R_MIN + Random.nextFloat() * (PARTICLE_R_MAX - PARTICLE_R_MIN),
                vx     = directionalVx + (Random.nextFloat() - 0.5f) * PARTICLE_RANDOM_SPREAD * particleSpeedMult,
                vy     = directionalVy + (Random.nextFloat() - 0.5f) * PARTICLE_RANDOM_SPREAD * particleSpeedMult,
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
            animator.isPaused -> {
                pendingStart = false
                animator.resume()
            }
            !animator.isRunning -> {
                randomizeParams()
                startupFrameCount = 0
                val w = width
                val h = height
                if (w <= 0 || h <= 0) {
                    // Layout not done yet - defer until onSizeChanged() fires
                    Timber.d("AudioWaveParticleView: startAnimation() deferred - view has no size yet")
                    pendingStart = true
                    return
                }
                pendingStart = false
                initParticles(w, h)
                wavePaint.strokeWidth = waveStrokeWidth
                animator.start()
                // S1277: covers the ordering where the host starts the animation after layout -
                // onSizeChanged already painted its frame with the previous session's palette,
                // and randomizeParams() above has just rolled a new one.
                if (animatorsDisabled()) renderStaticFrame()
            }
            // already running - no-op
        }
    }

    fun pauseAnimation() {
        if (animator.isRunning && !animator.isPaused) animator.pause()
    }

    fun stopAndReset() {
        pendingStart = false
        animator.cancel()
        time = 0f
        startupFrameCount = 0
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
        startupFrameCount = (startupFrameCount + 1).coerceAtMost(STARTUP_RAMP_FRAMES)

        // Semi-transparent overlay creates the motion-blur trail
        oc.drawRect(0f, 0f, w, h, fadeOverlayPaint)

        val startupProgress = startupFrameCount / STARTUP_RAMP_FRAMES.toFloat()
        val startupGain = 0.35f + 0.65f * startupProgress * (2f - startupProgress)
        val travelSpan = hypot(w, h) + stepPx * 6f
        val centerDrift = sin((time * 0.45f).toDouble()).toFloat() * minOf(w, h) * 0.02f
        val centerX = w * 0.5f + waveDirX * centerDrift
        val centerY = h * 0.5f + waveDirY * centerDrift
        val laneSpacing = minOf(w, h) * WAVE_LANE_SPACING_FRACTION
        val waveAlpha = 0.28f + 0.16f * startupGain

        // Sine-wave paths are sampled in a rotated coordinate space so each fresh start
        // can travel in any direction while keeping the draw cost close to the old version.
        for (j in 0 until waveCount) {
            val phaseShift = j * 0.8f
            val bandOffset = (j - (waveCount - 1) * 0.5f) * laneSpacing
            val waveEnvelope = 0.40f + 0.60f * abs(sin((time * 0.4f + j * 0.2f).toDouble())).toFloat()
            val amplitudePx = h * waveAmplitude * startupGain * waveEnvelope
            wavePath.rewind()
            var distance = -travelSpan * 0.5f
            var first = true
            while (distance <= travelSpan * 0.5f) {
                val displacement = sin((distance * 0.0105f + time + phaseShift).toDouble()).toFloat() * amplitudePx
                val drawX = centerX + waveDirX * distance + waveNormalX * (bandOffset + displacement)
                val drawY = centerY + waveDirY * distance + waveNormalY * (bandOffset + displacement)
                if (first) {
                    wavePath.moveTo(drawX, drawY)
                    first = false
                } else {
                    wavePath.lineTo(drawX, drawY)
                }
                distance += stepPx
            }
            wavePaint.color = hslToArgb((baseWaveHue + j * waveHueStep) % 360f, 0.80f, 0.65f, waveAlpha)
            oc.drawPath(wavePath, wavePaint)
        }

        // Drifting particles with edge bounce - radius, speed, hue vary per session
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            if (p.x < 0f || p.x > w) p.vx = -p.vx
            if (p.y < 0f || p.y > h) p.vy = -p.vy
            particlePaint.color = hslToArgb(p.hue, 0.90f, 0.70f, 0.38f + 0.32f * startupGain)
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
