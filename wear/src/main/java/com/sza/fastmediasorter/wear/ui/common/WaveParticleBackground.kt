package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// The animation is a cross-surface brand contract shared with the phone
// (`app_v2/.../ui/player/helpers/AudioWaveParticleView.kt`) and with the website's HTML canvas
// version. The four constants below are copied verbatim from that class and fix how fast the
// motion runs; the counts underneath are the watch's own, because the number of elements may be
// reduced for battery while the speed and the character of the motion may not change.
private const val TIME_INCREMENT = 0.002f
private const val SPEED_MULT_MIN = 0.5f
private const val SPEED_MULT_MAX = 1.5f
private const val HUE_SPREAD_DEG = 108f

// Watch counts: the phone draws 5..12 waves and 15..55 particles on a display many times larger.
private const val WAVE_COUNT_MIN = 3
private const val WAVE_COUNT_MAX = 6
private const val PARTICLE_COUNT_MIN = 8
private const val PARTICLE_COUNT_MAX = 20

private const val WAVE_STEP_PX = 20f
private const val WAVE_STEP_JITTER_MIN = 0.8f
private const val WAVE_STEP_JITTER_SPAN = 0.4f
private const val WAVE_STROKE_PX = 3f
private const val WAVE_AMPLITUDE_MIN = 0.28f
private const val WAVE_AMPLITUDE_MAX = 0.48f
private const val WAVE_LANE_SPACING_FRACTION = 0.038f
private const val WAVE_FREQUENCY = 0.0105f
private const val WAVE_PHASE_STEP = 0.8f
private const val WAVE_ENVELOPE_BASE = 0.40f
private const val WAVE_ENVELOPE_SWING = 0.60f
private const val WAVE_ENVELOPE_RATE = 0.4f
private const val WAVE_ENVELOPE_LANE_STEP = 0.2f
private const val WAVE_HUE_STEP_MIN = 8f
private const val WAVE_HUE_STEP_SPAN = 12f
private const val WAVE_SATURATION = 0.80f
private const val WAVE_LIGHTNESS = 0.65f
private const val WAVE_ALPHA = 0.44f

private const val PARTICLE_RADIUS_MIN = 1f
private const val PARTICLE_RADIUS_MAX = 6f
private const val PARTICLE_BASE_SPEED = 0.12f
private const val PARTICLE_DIRECTIONAL_BIAS = 0.42f
private const val PARTICLE_RANDOM_SPREAD = 0.28f
private const val PARTICLE_SATURATION = 0.90f
private const val PARTICLE_LIGHTNESS = 0.70f
private const val PARTICLE_ALPHA = 0.70f
private const val COUNTER_DRIFT_CHANCE = 0.18f
private const val COUNTER_DRIFT_SIGN = -0.35f

// Measured on the owner's watch: at full resolution and every frame this background cost about 1.5
// cores while playing. Both knobs below cut that without touching the contract - the buffer is
// rasterized at half the screen's resolution and blitted up, and frames are paced to 30 a second
// with the clock advanced by real elapsed time, so the motion covers the same ground per second as
// the phone's 60 does. Every length in this file is multiplied by the scale when a session is built,
// so halving the buffer changes how sharp the animation is and nothing about how fast it moves.
private const val RENDER_SCALE = 0.5f
private const val MIN_FRAME_INTERVAL_NANOS = 33_000_000L
private const val REFERENCE_FRAME_NANOS = 16_666_667f

private const val CENTER_DRIFT_RATE = 0.45f
private const val CENTER_DRIFT_FRACTION = 0.02f
private const val TRAVEL_MARGIN_STEPS = 6f
private const val TRAIL_ALPHA = 0.15f
private const val FULL_CIRCLE_DEG = 360f
private const val HALF = 0.5f

/**
 * The brand background for the audio player: drifting sine waves and particles, drawn at the same
 * speed as the phone and the website.
 *
 * @param running drives the frame loop. False cancels it and draws nothing - on a watch this
 * animation is the most expensive thing on the screen, so it must stop rather than keep drawing
 * while nobody is looking at it.
 */
@Composable
fun WaveParticleBackground(
    modifier: Modifier = Modifier,
    running: Boolean
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)

        // The look is accumulated rather than drawn in one pass - every frame lays a translucent
        // overlay and draws over it, which is what produces the motion trail. So the frame goes into
        // a buffer that survives frames, remembered on the measured size alone: the player recomposes
        // twice a second while the position ticks, and re-allocating this here would cost about a
        // megabyte of that.
        val bufferWidth = (widthPx * RENDER_SCALE).toInt().coerceAtLeast(1)
        val bufferHeight = (heightPx * RENDER_SCALE).toInt().coerceAtLeast(1)
        val buffer = remember(bufferWidth, bufferHeight) { ImageBitmap(bufferWidth, bufferHeight) }
        val bufferCanvas = remember(buffer) { Canvas(buffer) }
        val bufferScope = remember { CanvasDrawScope() }
        val bufferSize = remember(buffer) { Size(bufferWidth.toFloat(), bufferHeight.toFloat()) }
        val screenSize = remember(widthPx, heightPx) { IntSize(widthPx, heightPx) }
        val wavePath = remember { Path() }
        val session = remember(buffer) {
            WaveParticleSession(bufferWidth.toFloat(), bufferHeight.toFloat(), RENDER_SCALE)
        }

        LaunchedEffect(session, running) {
            if (!running) return@LaunchedEffect
            Timber.d("S2206: WaveParticleBackground running with TIME_INCREMENT=$TIME_INCREMENT")
            session.reroll()
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
                    if (!running) return@drawBehind
                    bufferScope.draw(this, layoutDirection, bufferCanvas, bufferSize) {
                        drawFrame(session, wavePath)
                    }
                    drawImage(image = buffer, dstSize = screenSize)
                }
        )
    }
}

/** One drifting dot. Position and velocity are in pixels of the screen this session was rolled for. */
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
 * clock the frames advance.
 */
private class WaveParticleSession(
    private val width: Float,
    private val height: Float,
    /** Buffer pixels per screen pixel. Every length and speed below is expressed in buffer pixels. */
    private val scale: Float
) {
    /**
     * Read in the draw phase only. A frame therefore invalidates drawing without recomposing the
     * player screen around it, which at 60 frames a second is the difference between a background
     * and a stall.
     */
    val time = mutableFloatStateOf(0f)

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

    /** True until the next frame wipes the buffer, so a restart never fades in the previous session. */
    var resetPending = true

    fun reroll() {
        waveCount = Random.nextInt(WAVE_COUNT_MIN, WAVE_COUNT_MAX + 1)
        stepPx = WAVE_STEP_PX * scale * (WAVE_STEP_JITTER_MIN + Random.nextFloat() * WAVE_STEP_JITTER_SPAN)
        strokePx = WAVE_STROKE_PX * scale
        baseHue = (Random.nextFloat() * FULL_CIRCLE_DEG - HUE_SPREAD_DEG * HALF + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
        hueStep = WAVE_HUE_STEP_MIN + Random.nextFloat() * WAVE_HUE_STEP_SPAN
        amplitudeFraction = WAVE_AMPLITUDE_MIN + Random.nextFloat() * (WAVE_AMPLITUDE_MAX - WAVE_AMPLITUDE_MIN)

        val angleRad = Math.toRadians((Random.nextFloat() * FULL_CIRCLE_DEG).toDouble())
        dirX = cos(angleRad).toFloat()
        dirY = sin(angleRad).toFloat()
        normalX = -dirY
        normalY = dirX

        particles = rollParticles()
        resetPending = true
    }

    /**
     * @param frames how many of the phone's 60-a-second frames this tick covers, so a watch drawing
     * half as often still travels the same distance per second.
     */
    fun advance(frames: Float) {
        time.floatValue += TIME_INCREMENT * frames
        for (particle in particles) {
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

    private fun rollParticles(): List<Particle> {
        val speedMult = SPEED_MULT_MIN + Random.nextFloat() * (SPEED_MULT_MAX - SPEED_MULT_MIN)
        val hueBase = Random.nextFloat() * FULL_CIRCLE_DEG
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
                hue = (hueBase + (Random.nextFloat() - HALF) * HUE_SPREAD_DEG + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
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

private fun DrawScope.drawFrame(session: WaveParticleSession, wavePath: Path) {
    if (session.resetPending) {
        drawRect(color = Color.Black)
        session.resetPending = false
    } else {
        drawRect(color = Color.Black.copy(alpha = TRAIL_ALPHA))
    }

    val time = session.time.floatValue
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
    for (particle in session.particles) {
        drawCircle(
            color = Color.hsl(particle.hue, PARTICLE_SATURATION, PARTICLE_LIGHTNESS, PARTICLE_ALPHA),
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
    val phaseShift = lane * WAVE_PHASE_STEP
    val bandOffset = (lane - (session.waveCount - 1) * HALF) * geometry.laneSpacing
    val envelopePhase = (time * WAVE_ENVELOPE_RATE + lane * WAVE_ENVELOPE_LANE_STEP).toDouble()
    val envelope = WAVE_ENVELOPE_BASE + WAVE_ENVELOPE_SWING * abs(sin(envelopePhase)).toFloat()
    val amplitude = size.height * session.amplitudeFraction * envelope

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
    drawPath(
        path = wavePath,
        color = Color.hsl(hue, WAVE_SATURATION, WAVE_LIGHTNESS, WAVE_ALPHA),
        style = Stroke(width = session.strokePx)
    )
}
