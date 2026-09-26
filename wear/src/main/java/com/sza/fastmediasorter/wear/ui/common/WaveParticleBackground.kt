package com.sza.fastmediasorter.wear.ui.common

import android.provider.Settings
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sza.fastmediasorter.wear.domain.model.WearAnimationPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// WAVE-PARTICLES section 3: the animation is a cross-product contract shared with the phone
// (`app_v2/.../ui/player/helpers/AudioWaveParticleView.kt`) and the website. Every constant under a
// "section 3.x" label is the contract's; a value that differs is an exception row in the catalog
// registry, never a local edit. A constant marked CHOSEN HERE is this renderer's own and the contract
// does not define it. WaveParticlesContractConstantsTest (app_v2 unit tests) pairs each contract
// constant with the reference implementation and fails on a drift. The watch runs the reduced profile
// (rule 13): fewer lines and particles and one fixed stroke width, the same clock, opacities, wash and
// ramp.

// WAVE-PARTICLES section 3.1.
private const val TIME_INCREMENT = 0.002f
private const val RAMP_FRAMES = 36
private const val REFERENCE_FRAME_NANOS = 16_666_667f

// WAVE-PARTICLES section 3.2, reduced profile.
private const val SPEED_MULT_MIN = 0.5f
private const val SPEED_MULT_MAX = 1.5f
private const val WAVE_COUNT_MIN = 3
private const val WAVE_COUNT_MAX = 6
private const val PARTICLE_COUNT_MIN = 6
private const val PARTICLE_COUNT_MAX = 20
private const val WAVE_STEP_PX = 20f
private const val WAVE_STEP_JITTER_MIN = 0.8f
private const val WAVE_STEP_JITTER_SPAN = 0.4f
private const val WAVE_AMPLITUDE_MIN = 0.28f
private const val WAVE_AMPLITUDE_MAX = 0.48f

// CHOSEN HERE, inside the range section 3.2 allows the reduced profile: one fixed stroke in [3, 6] px.
private const val WAVE_STROKE_PX = 5f

// WAVE-PARTICLES section 3.2, per particle.
private const val PARTICLE_RADIUS_MIN = 1f
private const val PARTICLE_RADIUS_MAX = 6f
private const val PARTICLE_BASE_SPEED = 0.12f
private const val PARTICLE_DIRECTIONAL_BIAS = 0.42f
private const val PARTICLE_RANDOM_SPREAD = 0.28f
private const val COUNTER_DRIFT_CHANCE = 0.18f
private const val COUNTER_DRIFT_SIGN = -0.35f

// WAVE-PARTICLES section 3.3, the DYNAMIC palette.
private const val HUE_SPREAD_DEG = 108f
private const val WAVE_HUE_STEP_MIN = 8f
private const val WAVE_HUE_STEP_SPAN = 12f

// WAVE-PARTICLES section 3.3, the banded palettes (S3557) - the phone renderer's names and values.
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

// WAVE-PARTICLES section 3.5 (rule 15), the tuning bounds - the phone renderer's names and values.
private const val INTENSITY_SCALE_MIN = 0f
private const val INTENSITY_SCALE_MAX = 1f
private const val SPEED_SCALE_MIN = 0.25f
private const val SPEED_SCALE_MAX = 2f
private const val DENSITY_SCALE_MIN = 0f
private const val DENSITY_SCALE_MAX = 1f
private const val TUNING_DEFAULT = 1f

// WAVE-PARTICLES section 3.4, dark surface.
private const val WASH_ALPHA = 38f / 255f
private const val WAVE_SATURATION = 0.80f
private const val WAVE_LIGHTNESS = 0.65f
private const val PARTICLE_SATURATION = 0.90f
private const val PARTICLE_LIGHTNESS = 0.70f

// WAVE-PARTICLES section 3.4: opacity = (base + gain * g) * OPACITY_SCALE, settling at 0.308 for lines
// and 0.490 for particles. Rule 16: nothing draws brighter than this; the watch's former 0.39 line
// opacity was a deviation. Rule 8: g rises from RAMP_GAIN_FLOOR to 1 over RAMP_FRAMES reference frames,
// ease-out.
private const val WAVE_ALPHA_BASE = 0.28f
private const val WAVE_ALPHA_GAIN = 0.16f
private const val PARTICLE_ALPHA_BASE = 0.38f
private const val PARTICLE_ALPHA_GAIN = 0.32f
private const val OPACITY_SCALE = 0.70f
private const val RAMP_GAIN_FLOOR = 0.35f
private const val RAMP_GAIN_SPAN = 0.65f
private const val RAMP_CURVE = 2f

// WAVE-PARTICLES section 3.4, the lane geometry.
private const val CENTER_DRIFT_RATE = 0.45f
private const val CENTER_DRIFT_FRACTION = 0.02f
private const val WAVE_LANE_SPACING_FRACTION = 0.038f
private const val TRAVEL_MARGIN_STEPS = 6f
private const val WAVE_FREQUENCY = 0.0105f
private const val WAVE_PHASE_STEP = 0.8f
private const val WAVE_ENVELOPE_BASE = 0.40f
private const val WAVE_ENVELOPE_SWING = 0.60f
private const val WAVE_ENVELOPE_RATE = 0.4f
private const val WAVE_ENVELOPE_LANE_STEP = 0.2f

// CHOSEN HERE, under rules 14 and 2. Measured on the owner's watch: at full resolution and every frame
// this background cost about 1.5 cores while playing. Both knobs below cut that without touching the
// contract - the buffer is rasterized at half the screen's resolution and blitted up (rule 14), and
// frames are paced to 30 a second with the clock, the ramp, the particles and the wash advanced by real
// elapsed time (rule 2), so the motion and the trail cover the same ground per second as a 60 Hz phone.
private const val RENDER_SCALE = 0.5f
private const val MIN_FRAME_INTERVAL_NANOS = 33_000_000L

private const val FULL_CIRCLE_DEG = 360f
private const val HUE_SECTOR_DEG = 60f
private const val HUE_SECTOR_CYAN = 3
private const val HUE_SECTOR_BLUE = 4
private const val CHANNEL_MAX = 255
private const val OPAQUE_ALPHA = 0xFF shl 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val HALF = 0.5f

/**
 * The brand background for the audio player: drifting sine waves and particles, drawn at the same
 * speed as the phone and the website.
 *
 * @param running drives the frame loop. False stops it and holds the frame already drawn - on a watch
 * this animation is the most expensive thing on the screen, so it must stop rather than keep drawing
 * while nobody is looking at it. A session that never drew shows the settled still frame instead.
 * @param intent what this instance's motion is FOR, which decides how strong a power level has to be
 * before it stops. The default is [AnimationIntent.AMBIENT] because the audio player was the first
 * caller; the backdrop drawn behind every other screen passes [AnimationIntent.DECORATIVE].
 * @param palette S3557: the paired phone's wallpaper palette. A change starts a new session, because
 * the hues are rolled once per session (section 3.3); the default is the watch's pre-S3557 look.
 * @param tuning S3557: the paired phone's wallpaper controls. A change keeps the session (rule 15).
 */
@Composable
fun WaveParticleBackground(
    modifier: Modifier = Modifier,
    running: Boolean,
    intent: AnimationIntent = AnimationIntent.AMBIENT,
    palette: WearAnimationPalette = WearAnimationPalette.DYNAMIC,
    tuning: WaveParticleTuning = WaveParticleTuning.DEFAULT
) {
    // Read in composition, not in the frame loop: the policy level is snapshot state, so a recovered
    // charge recomposes this and the loop below restarts on its own. Reading it inside the loop would
    // leave a frozen backdrop frozen until the screen was re-entered - and a frozen loop has no next
    // iteration in which to read anything. Rule 9: animator duration scale 0 is "motion not allowed".
    val resolver = LocalContext.current.contentResolver
    val systemMotionOff = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val animating = running && !systemMotionOff && WearPowerPolicy.mayAnimate(intent)
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        val bufferWidth = (widthPx * RENDER_SCALE).toInt().coerceAtLeast(1)
        val bufferHeight = (heightPx * RENDER_SCALE).toInt().coerceAtLeast(1)

        // One session and one buffer for the life of the composition: a size change carries both
        // (rule 12) rather than rolling a new session, and the player recomposing twice a second while
        // the position ticks must not reallocate either.
        val session = remember(palette) { WaveParticleSession(RENDER_SCALE, palette) }
        val backdrop = remember { BackdropBuffer() }
        val bufferScope = remember { CanvasDrawScope() }
        val screenSize = remember(widthPx, heightPx) { IntSize(widthPx, heightPx) }
        val wavePath = remember { Path() }
        val intensity = tuning.intensity.boundedOrDefault(INTENSITY_SCALE_MIN, INTENSITY_SCALE_MAX)
        SideEffect {
            session.speedScale = tuning.speed.boundedOrDefault(SPEED_SCALE_MIN, SPEED_SCALE_MAX)
            session.densityScale = tuning.density.boundedOrDefault(DENSITY_SCALE_MIN, DENSITY_SCALE_MAX)
        }

        LaunchedEffect(session, animating) {
            if (!animating) return@LaunchedEffect
            var lastFrameNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    val elapsed = if (lastFrameNanos == 0L) 0L else frameNanos - lastFrameNanos
                    if (elapsed >= MIN_FRAME_INTERVAL_NANOS || lastFrameNanos == 0L) {
                        lastFrameNanos = frameNanos
                        session.advance(elapsed / REFERENCE_FRAME_NANOS)
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Read on every draw so each clock step invalidates this draw and nothing else.
                    val clock = session.time.floatValue
                    val buffer = backdrop.ensure(bufferWidth, bufferHeight, session)
                    val frames = session.takePendingFrames()
                    if (session.resetPending || frames > 0f) {
                        bufferScope.draw(this, layoutDirection, backdrop.canvas, backdrop.size) {
                            renderSession(session, wavePath, FrameStep(frames, clock), animating)
                        }
                    }
                    // Rule 15: intensity is the opacity of the final present, dimming towards the host's
                    // own fill behind this layer rather than towards grey.
                    drawImage(image = buffer, dstSize = screenSize, alpha = intensity)
                }
        )
    }
}

/**
 * WAVE-PARTICLES rule 15: the three user controls, at their defaults indistinguishable from an untuned
 * backdrop. Held as handed over; the renderer clamps each to section 3.5 where it applies it.
 */
@Immutable
data class WaveParticleTuning(
    val intensity: Float = TUNING_DEFAULT,
    val speed: Float = TUNING_DEFAULT,
    val density: Float = TUNING_DEFAULT
) {
    companion object {
        val DEFAULT = WaveParticleTuning()
    }
}

/** A NaN would pass coerceIn untouched, so a non-finite value reads as the default. */
private fun Float.boundedOrDefault(min: Float, max: Float): Float =
    if (isFinite()) coerceIn(min, max) else TUNING_DEFAULT

/** One session's hue roll (WAVE-PARTICLES section 3.3), in degrees. */
internal class PaletteHues(
    val lineBase: Float,
    val lineStep: Float,
    val particleBase: Float,
    val particleSpread: Float
)

/**
 * The section 3.3 roll for [palette]. Shared with the watch-face style encoder, which feeds it a seeded
 * [random] so the face paints from the same bands this backdrop rolls from.
 */
internal fun rollPaletteHues(palette: WearAnimationPalette, random: Random): PaletteHues = when (palette) {
    WearAnimationPalette.DYNAMIC -> PaletteHues(
        lineBase = (random.nextFloat() * FULL_CIRCLE_DEG - HUE_SPREAD_DEG * HALF + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG,
        lineStep = WAVE_HUE_STEP_MIN + random.nextFloat() * WAVE_HUE_STEP_SPAN,
        particleBase = random.nextFloat() * FULL_CIRCLE_DEG,
        particleSpread = HUE_SPREAD_DEG
    )
    WearAnimationPalette.GREEN ->
        rollBandedHues(random, GREEN_HUE_BASE_MIN, GREEN_HUE_BASE_RANGE, GREEN_PARTICLE_HUE_BASE)
    WearAnimationPalette.PINK ->
        rollBandedHues(random, PINK_HUE_BASE_MIN, PINK_HUE_BASE_RANGE, PINK_PARTICLE_HUE_BASE)
    WearAnimationPalette.BLUE ->
        rollBandedHues(random, BLUE_HUE_BASE_MIN, BLUE_HUE_BASE_RANGE, BLUE_PARTICLE_HUE_BASE)
}

private fun rollBandedHues(random: Random, baseMin: Float, baseRange: Float, particleCentre: Float) = PaletteHues(
    lineBase = baseMin + random.nextFloat() * baseRange,
    lineStep = PALETTE_WAVE_STEP_MIN + random.nextFloat() * PALETTE_WAVE_STEP_RANGE,
    particleBase = particleCentre + (random.nextFloat() - HALF) * PALETTE_PARTICLE_SPREAD_DEG,
    particleSpread = PALETTE_PARTICLE_SPREAD_DEG
)

/**
 * Section 3.4, dark surface: the opaque line colour for [hue], as ARGB. The watch-face style encoder
 * paints its lanes with it, so the face and this backdrop share one saturation and lightness.
 */
internal fun waveLineArgb(hue: Float): Int = hslToArgb(hue, WAVE_SATURATION, WAVE_LIGHTNESS)

/** Section 3.4, dark surface: the opaque particle colour for [hue], as ARGB. */
internal fun particleArgb(hue: Float): Int = hslToArgb(hue, PARTICLE_SATURATION, PARTICLE_LIGHTNESS)

/** Plain HSL to opaque ARGB, kept free of Android and Compose types so a JVM unit test can run it. */
private fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
    val h = ((hue % FULL_CIRCLE_DEG) + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val sector = h / HUE_SECTOR_DEG
    val second = chroma * (1f - abs(sector % 2f - 1f))
    val (r, g, b) = when (sector.toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        HUE_SECTOR_CYAN -> Triple(0f, second, chroma)
        HUE_SECTOR_BLUE -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val m = lightness - chroma * HALF
    fun channel(value: Float): Int = ((value + m) * CHANNEL_MAX).roundToInt().coerceIn(0, CHANNEL_MAX)
    return OPAQUE_ALPHA or (channel(r) shl RED_SHIFT) or (channel(g) shl GREEN_SHIFT) or channel(b)
}

/** The reference frames a draw covers and the clock it draws at. */
private class FrameStep(val frames: Float, val clock: Float)

/**
 * The off-screen buffer the trail accumulates in. A new size gets the old buffer stretched into it
 * before anything draws over it, so a resize never shows an empty field (rule 12).
 */
private class BackdropBuffer {
    private var image: ImageBitmap? = null
    lateinit var canvas: Canvas
        private set
    var size: Size = Size.Zero
        private set

    /** The session last handed this buffer's size; a new palette brings a session that has none yet. */
    private var sizedSession: WaveParticleSession? = null

    fun ensure(width: Int, height: Int, session: WaveParticleSession): ImageBitmap {
        val current = image
        if (current != null && current.width == width && current.height == height) {
            if (sizedSession !== session) {
                sizedSession = session
                session.resize(width.toFloat(), height.toFloat())
            }
            return current
        }
        val next = ImageBitmap(width, height)
        val nextCanvas = Canvas(next)
        if (current != null) {
            nextCanvas.drawImageRect(
                image = current,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(current.width, current.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(width, height),
                paint = Paint()
            )
        }
        image = next
        canvas = nextCanvas
        size = Size(width.toFloat(), height.toFloat())
        sizedSession = session
        session.resize(width.toFloat(), height.toFloat())
        return next
    }
}

/** One drifting dot. Position and velocity are in buffer pixels. */
private class Particle(
    var x: Float,
    var y: Float,
    val radius: Float,
    var vx: Float,
    var vy: Float,
    val hue: Float
)

/**
 * Everything a single playback session re-randomizes, so two tracks never look identical, plus the
 * clock and the ramp the frames advance.
 */
private class WaveParticleSession(
    /** Buffer pixels per screen pixel. Every length and speed below is expressed in buffer pixels. */
    private val scale: Float,
    private val palette: WearAnimationPalette
) {
    /**
     * Read in the draw phase only. A frame therefore invalidates drawing without recomposing the
     * player screen around it, which at 60 frames a second is the difference between a background
     * and a stall.
     */
    val time = mutableFloatStateOf(0f)

    var width = 0f
        private set
    var height = 0f
        private set
    var waveCount = WAVE_COUNT_MIN
    var stepPx = WAVE_STEP_PX
    var strokePx = WAVE_STROKE_PX
    var baseHue = 0f
    var hueStep = WAVE_HUE_STEP_MIN
    var amplitudeFraction = WAVE_AMPLITUDE_MIN
    var dirX = 1f
    var dirY = 0f
    var normalX = 0f
    var normalY = 1f
    var particles: List<Particle> = emptyList()

    /** Rule 15: multiplies the clock only - the ramp, the wash and the particles keep real time. */
    var speedScale = TUNING_DEFAULT

    /** Rule 15: the share of the rolled particles drawn, so a change never re-rolls the session. */
    var densityScale = TUNING_DEFAULT

    /** The rolled count after [densityScale], rounded and never above the roll. */
    val activeParticleCount: Int
        get() = (particles.size * densityScale).roundToInt().coerceIn(0, particles.size)

    /** Reference frames since the session started, capped at [RAMP_FRAMES]. */
    private var rampFrames = 0f

    /** Reference frames advanced since the last draw consumed them. */
    private var pendingFrames = 0f
    private var rolled = false

    /** True until the next draw wipes the buffer, so a restart never fades in the previous session. */
    var resetPending = true

    /** Rule 8: the gain on amplitude and both opacities. */
    val gain: Float
        get() {
            val progress = rampFrames / RAMP_FRAMES
            return RAMP_GAIN_FLOOR + RAMP_GAIN_SPAN * progress * (RAMP_CURVE - progress)
        }

    /** The first size rolls the session; a later one re-seeds positions only and keeps every roll. */
    fun resize(newWidth: Float, newHeight: Float) {
        width = newWidth
        height = newHeight
        if (!rolled) {
            reroll()
            return
        }
        for (particle in particles) {
            particle.x = Random.nextFloat() * width
            particle.y = Random.nextFloat() * height
        }
    }

    private fun reroll() {
        rolled = true
        waveCount = Random.nextInt(WAVE_COUNT_MIN, WAVE_COUNT_MAX + 1)
        stepPx = WAVE_STEP_PX * scale * (WAVE_STEP_JITTER_MIN + Random.nextFloat() * WAVE_STEP_JITTER_SPAN)
        strokePx = WAVE_STROKE_PX * scale
        val hues = rollPaletteHues(palette, Random.Default)
        baseHue = hues.lineBase
        hueStep = hues.lineStep
        amplitudeFraction = WAVE_AMPLITUDE_MIN + Random.nextFloat() * (WAVE_AMPLITUDE_MAX - WAVE_AMPLITUDE_MIN)

        val angleRad = Math.toRadians((Random.nextFloat() * FULL_CIRCLE_DEG).toDouble())
        dirX = cos(angleRad).toFloat()
        dirY = sin(angleRad).toFloat()
        normalX = -dirY
        normalY = dirX

        particles = rollParticles(hues)
        rampFrames = 0f
        pendingFrames = 0f
        resetPending = true
    }

    /**
     * @param frames how many of the phone's 60-a-second frames this tick covers, so a watch drawing
     * half as often still travels the same distance per second.
     */
    fun advance(frames: Float) {
        time.floatValue += TIME_INCREMENT * speedScale * frames
        rampFrames = (rampFrames + frames).coerceAtMost(RAMP_FRAMES.toFloat())
        pendingFrames += frames
        for (index in 0 until activeParticleCount) {
            val particle = particles[index]
            particle.x += particle.vx * frames
            particle.y += particle.vy * frames
            if (particle.x < 0f || particle.x > width) {
                particle.vx = -particle.vx
            }
            if (particle.y < 0f || particle.y > height) {
                particle.vy = -particle.vy
            }
        }
    }

    fun takePendingFrames(): Float {
        val taken = pendingFrames
        pendingFrames = 0f
        return taken
    }

    private fun rollParticles(hues: PaletteHues): List<Particle> {
        val speedMult = SPEED_MULT_MIN + Random.nextFloat() * (SPEED_MULT_MAX - SPEED_MULT_MIN)
        val count = Random.nextInt(PARTICLE_COUNT_MIN, PARTICLE_COUNT_MAX + 1)
        return List(count) {
            val speed = (PARTICLE_BASE_SPEED + Random.nextFloat() * PARTICLE_DIRECTIONAL_BIAS) * speedMult * scale
            // Most dots travel with the waves; a few drift back against them, which is what keeps the
            // field from reading as one sheet sliding across the screen.
            val driftSign = if (Random.nextFloat() < COUNTER_DRIFT_CHANCE) COUNTER_DRIFT_SIGN else 1f
            val spread = PARTICLE_RANDOM_SPREAD * speedMult * scale
            val radiusSpan = PARTICLE_RADIUS_MAX - PARTICLE_RADIUS_MIN
            Particle(
                x = Random.nextFloat() * width,
                y = Random.nextFloat() * height,
                radius = (PARTICLE_RADIUS_MIN + Random.nextFloat() * radiusSpan) * scale,
                vx = dirX * speed * driftSign + (Random.nextFloat() - HALF) * spread,
                vy = dirY * speed * driftSign + (Random.nextFloat() - HALF) * spread,
                hue = (hues.particleBase + (Random.nextFloat() - HALF) * hues.particleSpread + FULL_CIRCLE_DEG) %
                    FULL_CIRCLE_DEG
            )
        }
    }
}

/** Where the wave band sits and how far it travels this frame - derived per frame, not per wave. */
private class WaveGeometry(
    val centerX: Float,
    val centerY: Float,
    val travelSpan: Float,
    val laneSpacing: Float
)

/**
 * A fresh session starts from a buffer washed at full opacity. When it may not animate, it is brought
 * to the frame the animation would have reached after [RAMP_FRAMES] frames (rule 9) - one pass would
 * show a dim, trail-less frame at the ramp's floor. A frozen session that already drew holds its frame.
 */
private fun DrawScope.renderSession(
    session: WaveParticleSession,
    wavePath: Path,
    step: FrameStep,
    animating: Boolean
) {
    if (!session.resetPending) {
        drawFrame(session, wavePath, step)
        return
    }
    session.resetPending = false
    drawRect(color = Color.Black)
    if (animating) {
        drawFrame(session, wavePath, FrameStep(1f, step.clock))
        return
    }
    repeat(RAMP_FRAMES) {
        session.advance(1f)
        drawFrame(session, wavePath, FrameStep(1f, session.time.floatValue))
    }
    session.takePendingFrames()
}

private fun DrawScope.drawFrame(session: WaveParticleSession, wavePath: Path, step: FrameStep) {
    // The wash that leaves exactly what `frames` washes at WASH_ALPHA would leave (section 4, 0.10).
    val washAlpha = 1f - (1f - WASH_ALPHA).pow(step.frames)
    drawRect(color = Color.Black.copy(alpha = washAlpha))

    val time = step.clock
    val gain = session.gain
    val shorterEdge = minOf(size.width, size.height)
    val drift = sin((time * CENTER_DRIFT_RATE).toDouble()).toFloat() * shorterEdge * CENTER_DRIFT_FRACTION
    val geometry = WaveGeometry(
        centerX = size.width * HALF + session.dirX * drift,
        centerY = size.height * HALF + session.dirY * drift,
        travelSpan = hypot(size.width, size.height) + session.stepPx * TRAVEL_MARGIN_STEPS,
        laneSpacing = shorterEdge * WAVE_LANE_SPACING_FRACTION
    )

    for (lane in 0 until session.waveCount) {
        drawWave(session, geometry, lane, time, wavePath)
    }
    val particleAlpha = (PARTICLE_ALPHA_BASE + PARTICLE_ALPHA_GAIN * gain) * OPACITY_SCALE
    for (index in 0 until session.activeParticleCount) {
        val particle = session.particles[index]
        drawCircle(
            color = Color.hsl(particle.hue, PARTICLE_SATURATION, PARTICLE_LIGHTNESS, particleAlpha),
            radius = particle.radius,
            center = Offset(particle.x, particle.y)
        )
    }
}

/**
 * Samples one wave in a rotated coordinate space, so a session can travel in any direction while
 * costing the same as an axis-aligned one.
 */
private fun DrawScope.drawWave(
    session: WaveParticleSession,
    geometry: WaveGeometry,
    lane: Int,
    time: Float,
    wavePath: Path
) {
    val gain = session.gain
    val phaseShift = lane * WAVE_PHASE_STEP
    val bandOffset = (lane - (session.waveCount - 1) * HALF) * geometry.laneSpacing
    val envelopePhase = (time * WAVE_ENVELOPE_RATE + lane * WAVE_ENVELOPE_LANE_STEP).toDouble()
    val envelope = WAVE_ENVELOPE_BASE + WAVE_ENVELOPE_SWING * abs(sin(envelopePhase)).toFloat()
    val amplitude = size.height * session.amplitudeFraction * gain * envelope

    wavePath.reset()
    var distance = -geometry.travelSpan * HALF
    var first = true
    while (distance <= geometry.travelSpan * HALF) {
        val displacement = sin((distance * WAVE_FREQUENCY + time + phaseShift).toDouble()).toFloat() * amplitude
        val pointX = geometry.centerX + session.dirX * distance + session.normalX * (bandOffset + displacement)
        val pointY = geometry.centerY + session.dirY * distance + session.normalY * (bandOffset + displacement)
        if (first) {
            wavePath.moveTo(pointX, pointY)
            first = false
        } else {
            wavePath.lineTo(pointX, pointY)
        }
        distance += session.stepPx
    }

    val hue = (session.baseHue + lane * session.hueStep) % FULL_CIRCLE_DEG
    val alpha = (WAVE_ALPHA_BASE + WAVE_ALPHA_GAIN * gain) * OPACITY_SCALE
    drawPath(
        path = wavePath,
        color = Color.hsl(hue, WAVE_SATURATION, WAVE_LIGHTNESS, alpha),
        style = Stroke(width = session.strokePx)
    )
}
