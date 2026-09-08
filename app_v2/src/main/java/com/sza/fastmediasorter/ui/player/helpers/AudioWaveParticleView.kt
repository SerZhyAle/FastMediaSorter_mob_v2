package com.sza.fastmediasorter.ui.player.helpers

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import com.sza.fastmediasorter.core.util.AnimationIntent
import com.sza.fastmediasorter.core.util.AnimationPolicy
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom View rendering a procedural wave + drifting particle animation as an audio background.
 *
 * Algorithm:
 *  - 5-12 sine-wave paths drawn per frame (count, stroke, amplitude, color randomized per session).
 *  - 15-55 drifting particles that bounce off view edges (count, size, speed, hue randomized).
 *  - Motion-blur trail effect via a semi-transparent overlay drawn onto an off-screen Bitmap each
 *    frame, avoiding expensive post-processing blur filters.
 *
 * S1287: the palette follows the host's theme. The buffer fill, the trail overlay and the lightness of
 * waves and particles are mirrored for a light theme - dark strokes on a light field - so the animation
 * stops being a black rectangle on a light screen. Hues are untouched; do not restore a hardcoded black.
 *
 * All visual parameters are re-randomized on each fresh [startAnimation] call so every
 * playback session looks distinct.
 *
 * Public lifecycle API mirrors [AudioBreathingBarsView]:
 *  - [startAnimation] - called when audio starts playing
 *  - [renderFreshStaticFrame] - draws one settled randomized frame without animation
 *  - [pauseAnimation] - called when audio is paused (retains last frame)
 *  - [stopAndReset]   - cancels animator, clears buffer, resets time
 */
class AudioWaveParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** S2223: selectable animation color palettes. */
    enum class AnimationColorPalette(val key: String) {
        DYNAMIC(com.sza.fastmediasorter.domain.model.AppSettings.ANIMATION_PALETTE_DYNAMIC),
        GREEN(com.sza.fastmediasorter.domain.model.AppSettings.ANIMATION_PALETTE_GREEN),
        PINK(com.sza.fastmediasorter.domain.model.AppSettings.ANIMATION_PALETTE_PINK),
        BLUE(com.sza.fastmediasorter.domain.model.AppSettings.ANIMATION_PALETTE_BLUE);

        companion object {
            fun fromKeyOrDefault(key: String?): AnimationColorPalette =
                entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DYNAMIC
        }
    }

    /** Selected color palette for procedural waves and particles. */
    var palette: AnimationColorPalette = AnimationColorPalette.DYNAMIC

    companion object {
        // Must match the speed of the HTML canvas version: time += 0.002 per animation frame.
        // ValueAnimator fires ~60fps; 0.002 per tick gives ~0.12/s drift.
        private const val TIME_INCREMENT = 0.002f

        // Randomization ranges - normal devices
        private const val WAVE_COUNT_MIN = 5
        private const val WAVE_COUNT_MAX = 12
        private const val STEP_PX_BASE = 20f // ±20 % → 16..24 px
        private const val STROKE_MIN = 3f
        private const val STROKE_MAX = 6f
        private const val AMPLITUDE_MIN = 0.28f // fraction of view height
        private const val AMPLITUDE_MAX = 0.48f
        private const val PARTICLE_MIN = 15
        private const val PARTICLE_MAX = 55
        private const val PARTICLE_R_MIN = 1f // px
        private const val PARTICLE_R_MAX = 6f // px
        private const val SPEED_MULT_MIN = 0.5f
        private const val SPEED_MULT_MAX = 1.5f
        private const val HUE_SPREAD_DEG = 108f // ±30 % of 360°
        private const val PALETTE_WAVE_STEP_MIN = 2f
        private const val PALETTE_WAVE_STEP_RANGE = 4f
        private const val PALETTE_PARTICLE_SPREAD_DEG = 30f

        private const val GREEN_HUE_BASE_MIN = 95f
        private const val GREEN_HUE_BASE_RANGE = 35f
        private const val GREEN_PARTICLE_HUE_BASE = 115f

        private const val PINK_HUE_BASE_MIN = 305f
        private const val PINK_HUE_BASE_RANGE = 30f
        private const val PINK_PARTICLE_HUE_BASE = 320f

        private const val BLUE_HUE_BASE_MIN = 200f
        private const val BLUE_HUE_BASE_RANGE = 30f
        private const val BLUE_PARTICLE_HUE_BASE = 215f

        private const val STARTUP_RAMP_FRAMES = 36
        private const val WAVE_LANE_SPACING_FRACTION = 0.038f
        private const val PARTICLE_DIRECTIONAL_BIAS = 0.42f
        private const val PARTICLE_RANDOM_SPREAD = 0.28f
        private const val COUNTER_DRIFT_CHANCE = 0.18f

        // Reduced limits for low-RAM / weak devices
        private const val WAVE_COUNT_MIN_LOW = 3
        private const val WAVE_COUNT_MAX_LOW = 6
        private const val PARTICLE_MIN_LOW = 6
        private const val PARTICLE_MAX_LOW = 18

        // S1277: the look is accumulated, not drawn in one pass - each tick lays a translucent
        // overlay and draws over it, and the first STARTUP_RAMP_FRAMES ticks ramp amplitude and
        // alpha up from 35 %. The static path runs that ramp to completion so a device with
        // animations off gets the frame the animator would have reached, not a near-empty buffer.
        private const val STATIC_FRAME_PASSES = STARTUP_RAMP_FRAMES

        // Default when the animator scale cannot be read; matches the platform default.
        private const val ANIMATOR_SCALE_DEFAULT = 1f

        // S1287: the palette is mirrored across the lightness scale, never across the hue wheel - a
        // literal negative of a random rainbow reads as a different animation, not an inverted one.
        // The dark values are the originals; the light ones are their reflections.
        private const val FADE_ALPHA = 38
        private const val FADE_CHANNEL_DARK = 10
        private const val FADE_CHANNEL_LIGHT = 245
        private const val WAVE_LIGHTNESS_DARK = 0.65f
        private const val WAVE_LIGHTNESS_LIGHT = 0.35f
        private const val PARTICLE_LIGHTNESS_DARK = 0.70f
        private const val PARTICLE_LIGHTNESS_LIGHT = 0.30f

        // Fully opaque blit - the default, and the scale [backdropIntensity] is expressed against.
        private const val BLIT_ALPHA_OPAQUE = 255f

        // S2730: the bounds the two tuning scales are clamped to here, so a caller that reads a stored
        // value cannot drive the renderer outside what it was measured at. The settings layer names the
        // same bounds for its sliders; this clamp is the renderer's own last word.
        private const val SPEED_SCALE_MIN = 0.25f
        private const val SPEED_SCALE_MAX = 2f
        private const val DENSITY_SCALE_MIN = 0f
        private const val DENSITY_SCALE_MAX = 1f
    }

    /**
     * S1287: true when the host screen runs a light theme. Read from the theme rather than from the
     * system night mode, because the app carries its own theme setting and the two can disagree -
     * trusting the system alone would leave a black rectangle on a light screen. The night mask is the
     * fallback, which is correct: with no explicit choice the theme follows the system anyway.
     */
    private val lightTheme: Boolean = resolveLightTheme(context)

    private val bufferFillColor = if (lightTheme) Color.WHITE else Color.BLACK
    private val waveLightness = if (lightTheme) WAVE_LIGHTNESS_LIGHT else WAVE_LIGHTNESS_DARK
    private val particleLightness = if (lightTheme) PARTICLE_LIGHTNESS_LIGHT else PARTICLE_LIGHTNESS_DARK

    /**
     * True if [startAnimation] was called while the View had no size yet (layout pending).
     * The deferred start fires in [onSizeChanged] once real dimensions are known.
     */
    private var pendingStart = false

    /** True when a static frame was requested before this view received a real size. */
    private var pendingStaticFrame = false

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

    /** S2730: the count the session rolled, before [particleDensityScale] is applied to it. */
    private var particleCountBase = 55

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

    // A translucent wash of the background's own colour: it pulls the previous frame towards the fill
    // rather than towards grey, which is what keeps the trail clean in either theme.
    private val fadeOverlayPaint = Paint().apply {
        val channel = if (lightTheme) FADE_CHANNEL_LIGHT else FADE_CHANNEL_DARK
        color = Color.argb(FADE_ALPHA, channel, channel, channel)
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

    /**
     * S2729: visible strength of the finished frame, 0f..1f, applied to the single blit in [onDraw].
     * That blit is the one output path both the animated and the static wallpaper end on, so one lever
     * dims both and adds no drawing pass; the weakened frame blends towards the host's own background
     * colour rather than towards grey.
     *
     * It is per instance rather than a companion constant because three surfaces share this class -
     * the launcher backdrop, the player's audio visualizer and the welcome screen - and only the
     * backdrop is meant to fade. A constant would have taken the visualizer down with it.
     */
    var backdropIntensity: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            field = clamped
            blitPaint.alpha = (clamped * BLIT_ALPHA_OPAQUE).toInt()
            invalidate()
        }

    /**
     * S2730: multiplier on the per-frame time advance, 1f being the shipped speed.
     *
     * Per instance for [backdropIntensity]'s reason - three surfaces share this class and only the
     * launcher backdrop is user-tunable - and defaulting to 1f so a consumer that never assigns it
     * animates exactly as before.
     */
    var animationSpeedScale: Float = 1f
        set(value) {
            field = value.coerceIn(SPEED_SCALE_MIN, SPEED_SCALE_MAX)
        }

    /**
     * S2730: multiplier on the seeded particle count, 1f being the count the session rolled.
     *
     * Scaling the rolled count rather than the roll's bounds keeps the per-session variety the class
     * is built around: at 1f the frame is byte-identical to the pre-S2730 one, and 0f draws the waves
     * with no particles at all.
     */
    var particleDensityScale: Float = 1f
        set(value) {
            val clamped = value.coerceIn(DENSITY_SCALE_MIN, DENSITY_SCALE_MAX)
            if (clamped == field) return
            field = clamped
            particleCountCurrent = scaledParticleCount()
            if (width <= 0 || height <= 0) return
            initParticles(width, height)
            // A frozen frame - the static wallpaper mode, or an animation the power policy stopped - has
            // no tick left in which to notice the new particle set, and onDraw only blits the buffer the
            // ticks accumulated. Without this rebuild the slider would appear dead in exactly the two
            // states a user is most likely to be looking at while tuning it.
            if (animator.isRunning) invalidate() else renderStaticFrame()
        }

    private val wavePath = Path()

    // Drives time increments and invalidation; actual animation value is unused.
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 10_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            advanceTime()
            tick()
            invalidate()
        }
    }

    // ──────────────────── Lifecycle ────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w <= 0 || h <= 0) return
        // S2678: the outgoing buffer is carried into the new one, stretched to the new size, before
        // anything draws on top. The trail this view shows is accumulated - each tick lays a
        // semi-transparent overlay over the frames before it - so a buffer that starts as flat fill
        // has no trail to show, and the single pass a resize can afford cannot rebuild one. Copying
        // keeps the look continuous across a rotation at the cost of one bitmap blit.
        val previousBitmap = offBitmap
        val resized = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val resizedCanvas = Canvas(resized).also { it.drawColor(bufferFillColor) }
        if (previousBitmap != null && !previousBitmap.isRecycled) {
            resizedCanvas.drawBitmap(
                previousBitmap,
                Rect(0, 0, previousBitmap.width, previousBitmap.height),
                Rect(0, 0, w, h),
                null,
            )
        }
        previousBitmap?.recycle()
        offBitmap = resized
        offCanvas = resizedCanvas
        initParticles(w, h)
        if (pendingStaticFrame) {
            pendingStaticFrame = false
            renderFreshStaticFrame()
            return
        }
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
        //
        // S2678: the full ramp belongs to the FIRST sizing only. Its 36 passes exist to raise the
        // amplitude from zero to a settled frame, and after that [startupFrameCount] is already at
        // its ceiling - so on a resize the same 36 passes redraw a full-amplitude frame 36 times to
        // land on the one a single pass produces. Measured on a Galaxy S21+ with animations off,
        // that cost 470 ms of the 483 ms layout the rotation spent, because this runs inside
        // onSizeChanged and onSizeChanged runs inside the layout pass.
        if (animatorsDisabled()) {
            if (oldW == 0 && oldH == 0) renderStaticFrame() else renderResizedFrame()
        }
        Timber.d("S2678: onSizeChanged ${w}x$h from ${oldW}x$oldH animatorsOff=${animatorsDisabled()}")
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

    /**
     * Repaints the buffer a resize just blacked out, at the amplitude the view already reached.
     *
     * One pass, not [STATIC_FRAME_PASSES]: the ramp those passes drive is already finished by the
     * time a resize arrives, so repeating it changes nothing about the frame and only costs the
     * layout pass it runs inside (S2678).
     */
    private fun renderResizedFrame() {
        wavePaint.strokeWidth = waveStrokeWidth
        advanceTime()
        tick()
        invalidate()
    }

    /** Builds one complete frame without the animator, then shows it. See [STATIC_FRAME_PASSES]. */
    private fun renderStaticFrame() {
        wavePaint.strokeWidth = waveStrokeWidth
        repeat(STATIC_FRAME_PASSES) {
            advanceTime()
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
        val pMin = if (isLowRam) PARTICLE_MIN_LOW else PARTICLE_MIN
        val pMax = if (isLowRam) PARTICLE_MAX_LOW else PARTICLE_MAX

        waveCount = Random.nextInt(waveMin, waveMax + 1)
        stepPx = STEP_PX_BASE * (0.8f + Random.nextFloat() * 0.4f) // ±20 %
        waveStrokeWidth = STROKE_MIN + Random.nextFloat() * (STROKE_MAX - STROKE_MIN)
        when (palette) {
            AnimationColorPalette.DYNAMIC -> {
                baseWaveHue = (Random.nextFloat() * 360f - HUE_SPREAD_DEG / 2f + 360f) % 360f
                waveHueStep = 8f + Random.nextFloat() * 12f // 8..20°
                particleHueBase = Random.nextFloat() * 360f
            }
            AnimationColorPalette.GREEN -> {
                baseWaveHue = GREEN_HUE_BASE_MIN + Random.nextFloat() * GREEN_HUE_BASE_RANGE
                waveHueStep = PALETTE_WAVE_STEP_MIN + Random.nextFloat() * PALETTE_WAVE_STEP_RANGE
                particleHueBase = GREEN_PARTICLE_HUE_BASE + (Random.nextFloat() - 0.5f) * PALETTE_PARTICLE_SPREAD_DEG
            }
            AnimationColorPalette.PINK -> {
                baseWaveHue = PINK_HUE_BASE_MIN + Random.nextFloat() * PINK_HUE_BASE_RANGE
                waveHueStep = PALETTE_WAVE_STEP_MIN + Random.nextFloat() * PALETTE_WAVE_STEP_RANGE
                particleHueBase = PINK_PARTICLE_HUE_BASE + (Random.nextFloat() - 0.5f) * PALETTE_PARTICLE_SPREAD_DEG
            }
            AnimationColorPalette.BLUE -> {
                baseWaveHue = BLUE_HUE_BASE_MIN + Random.nextFloat() * BLUE_HUE_BASE_RANGE
                waveHueStep = PALETTE_WAVE_STEP_MIN + Random.nextFloat() * PALETTE_WAVE_STEP_RANGE
                particleHueBase = BLUE_PARTICLE_HUE_BASE + (Random.nextFloat() - 0.5f) * PALETTE_PARTICLE_SPREAD_DEG
            }
        }
        waveAmplitude = AMPLITUDE_MIN + Random.nextFloat() * (AMPLITUDE_MAX - AMPLITUDE_MIN)
        particleSpeedMult = SPEED_MULT_MIN + Random.nextFloat() * (SPEED_MULT_MAX - SPEED_MULT_MIN)
        particleCountBase = Random.nextInt(pMin, pMax + 1)
        particleCountCurrent = scaledParticleCount()
        waveDirectionAngleDeg = Random.nextFloat() * 360f

        val angleRad = Math.toRadians(waveDirectionAngleDeg.toDouble())
        waveDirX = cos(angleRad).toFloat()
        waveDirY = sin(angleRad).toFloat()
        waveNormalX = -waveDirY
        waveNormalY = waveDirX
    }

    /** S2730: one tick of the shared clock, scaled by [animationSpeedScale]. */
    private fun advanceTime() {
        time += TIME_INCREMENT * animationSpeedScale
    }

    /** S2730: the rolled particle count after [particleDensityScale], never above the roll. */
    private fun scaledParticleCount(): Int =
        (particleCountBase * particleDensityScale).roundToInt().coerceIn(0, particleCountBase)

    private fun initParticles(w: Int, h: Int) {
        particles.clear()
        val particleSpread = if (palette == AnimationColorPalette.DYNAMIC) {
            HUE_SPREAD_DEG
        } else {
            PALETTE_PARTICLE_SPREAD_DEG
        }
        repeat(particleCountCurrent) {
            val directionalSpeed = (0.12f + Random.nextFloat() * PARTICLE_DIRECTIONAL_BIAS) * particleSpeedMult
            val driftSign = if (Random.nextFloat() < COUNTER_DRIFT_CHANCE) -0.35f else 1f
            val directionalVx = waveDirX * directionalSpeed * driftSign
            val directionalVy = waveDirY * directionalSpeed * driftSign
            particles += Particle(
                x = Random.nextFloat() * w,
                y = Random.nextFloat() * h,
                radius = PARTICLE_R_MIN + Random.nextFloat() * (PARTICLE_R_MAX - PARTICLE_R_MIN),
                vx = directionalVx + (Random.nextFloat() - 0.5f) * PARTICLE_RANDOM_SPREAD * particleSpeedMult,
                vy = directionalVy + (Random.nextFloat() - 0.5f) * PARTICLE_RANDOM_SPREAD * particleSpeedMult,
                hue = (particleHueBase + (Random.nextFloat() - 0.5f) * particleSpread + 360f) % 360f
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        offBitmap?.let { canvas.drawBitmap(it, 0f, 0f, blitPaint) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AnimationPolicy.addLevelListener(policyListener)
    }

    override fun onDetachedFromWindow() {
        AnimationPolicy.removeLevelListener(policyListener)
        super.onDetachedFromWindow()
        animator.cancel()
        offBitmap?.recycle()
        offBitmap = null
        offCanvas = null
    }

    // ──────────────────── Public API ────────────────────

    /**
     * S2536: what this instance's motion is FOR. The audio player leaves the default; the launcher
     * desktop sets [AnimationIntent.DECORATIVE], which is what lets one class be both the visualizer
     * that survives the cosmetic switch and the wallpaper that does not.
     */
    var intent: AnimationIntent = AnimationIntent.AMBIENT

    /** True while the policy, not the host, is what is holding the animation still. */
    private var frozenByPolicy = false

    // Fires on the settings collector's thread, so the work is posted to the view's own thread.
    private val policyListener: () -> Unit = { post { refreshPolicy() } }

    /**
     * S2536: re-asks the policy after the level moved. Without this a backdrop frozen at ten percent
     * would stay frozen after the charge recovered until the view was recreated - a paused animator
     * has no draw pass left in which to notice the change on its own.
     */
    fun refreshPolicy() {
        Timber.d("S2536: visualizer refresh intent=$intent level=${AnimationPolicy.level}")
        if (AnimationPolicy.mayAnimate(intent)) {
            if (frozenByPolicy) startAnimation()
        } else {
            freezeForPolicy()
        }
    }

    /**
     * Pauses rather than cancels when a session is already running, so the buffer keeps the frame it
     * had and the view reads as a still image instead of going black (S1277).
     */
    private fun freezeForPolicy() {
        if (frozenByPolicy) return
        frozenByPolicy = true
        if (animator.isRunning) pauseAnimation() else renderFreshStaticFrame()
    }

    fun startAnimation() {
        if (!AnimationPolicy.mayAnimate(intent)) {
            freezeForPolicy()
            return
        }
        frozenByPolicy = false
        beginAnimatorSession()
    }

    private fun beginAnimatorSession() {
        when {
            animator.isPaused -> {
                pendingStaticFrame = false
                pendingStart = false
                animator.resume()
            }
            !animator.isRunning -> {
                pendingStaticFrame = false
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

    /** Draws one newly randomized settled frame without starting the animator. */
    fun renderFreshStaticFrame() {
        pendingStart = false
        animator.cancel()
        time = 0f
        startupFrameCount = 0
        val w = width
        val h = height
        if (w <= 0 || h <= 0) {
            pendingStaticFrame = true
            return
        }
        pendingStaticFrame = false
        randomizeParams()
        initParticles(w, h)
        offCanvas?.drawColor(bufferFillColor)
        renderStaticFrame()
    }

    fun pauseAnimation() {
        if (animator.isRunning && !animator.isPaused) animator.pause()
    }

    fun stopAndReset() {
        pendingStart = false
        pendingStaticFrame = false
        animator.cancel()
        time = 0f
        startupFrameCount = 0
        offCanvas?.drawColor(bufferFillColor)
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
        val waveAlpha = (0.28f + 0.16f * startupGain) * 0.70f

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
            wavePaint.color =
                hslToArgb((baseWaveHue + j * waveHueStep) % 360f, 0.80f, waveLightness, waveAlpha)
            oc.drawPath(wavePath, wavePaint)
        }

        // Drifting particles with edge bounce - radius, speed, hue vary per session
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            if (p.x < 0f || p.x > w) p.vx = -p.vx
            if (p.y < 0f || p.y > h) p.vy = -p.vy
            particlePaint.color =
                hslToArgb(p.hue, 0.90f, particleLightness, (0.38f + 0.32f * startupGain) * 0.70f)
            oc.drawCircle(p.x, p.y, p.radius, particlePaint)
        }
    }

    // ──────────────────── Color ────────────────────

    /**
     * S1287: the theme's own answer first, the system night mode second. Returning `false` on both
     * failures is deliberate - it lands on the palette this view has always drawn.
     */
    private fun resolveLightTheme(context: Context): Boolean {
        val value = TypedValue()
        if (context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, value, true)) {
            return value.data != 0
        }
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask != Configuration.UI_MODE_NIGHT_YES
    }

    /** Converts HSL + alpha to an ARGB integer. h: [0,360], s/l/a: [0,1]. */
    private fun hslToArgb(h: Float, s: Float, l: Float, a: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c + m, x + m, m)
            h < 120f -> Triple(x + m, c + m, m)
            h < 180f -> Triple(m, c + m, x + m)
            h < 240f -> Triple(m, x + m, c + m)
            h < 300f -> Triple(x + m, m, c + m)
            else -> Triple(c + m, m, x + m)
        }
        return Color.argb(
            (a * 255f).toInt(),
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }
}
