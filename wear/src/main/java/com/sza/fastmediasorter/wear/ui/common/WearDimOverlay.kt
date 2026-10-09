package com.sza.fastmediasorter.wear.ui.common

import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.util.WearUnitDateTimeFormatter
import com.sza.fastmediasorter.wear.domain.model.UnitSystem
import com.sza.fastmediasorter.wear.domain.usecase.ObserveHeadingUseCase
import com.sza.fastmediasorter.wear.ui.common.dimresponse.TapResponseGeometry
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSwallow
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.random.Random

/** How long the acknowledgement ring takes to spread out and fade - slow on purpose (S3370 doubled it). */
private const val TAP_MARK_DURATION_MS = 2800

/** The ring is born at the size the old static mark had, so the tap point is still found at once. */
private val TAP_MARK_START_RADIUS = 6.dp

/** Wide enough to read as a wave, small enough to leave most of the glass dark. */
private val TAP_MARK_END_RADIUS = 96.dp

private val TAP_MARK_STROKE = 2.dp

/** S3370: the spark pair is the "fast" half of the response - visibly shorter than the ring. */
private const val SPARK_DURATION_MS = 700

private val SPARK_LENGTH_DP = 40.dp

private val SPARK_PEAK_WIDTH_DP = 6.dp

private val SPARK_TRAVEL_DP = 40.dp

private const val SPARK_PROFILE_STEPS = 12

private const val SPARK_STRIPE_HALF = 0.5f

private const val FULL_CIRCLE_DEGREES = 360f

private const val SPARK_NORTH_COLOR_ARGB = 0xFFE53935

private const val SPARK_SOUTH_COLOR_ARGB = 0xFF448AFF

private val SPARK_NORTH_COLOR = Color(SPARK_NORTH_COLOR_ARGB)

private val SPARK_SOUTH_COLOR = Color(SPARK_SOUTH_COLOR_ARGB)

/** S4139: long enough to read the time at a glance, short enough to hand the glass back to the dark. */
private const val TAP_CLOCK_VISIBLE_MS = 1000

/** Room between the tap point and the clock, so the finger that tapped does not cover it. */
private val TAP_CLOCK_GAP = 24.dp

private const val MAX_BRIGHTNESS = 1.0f

/**
 * S1683: an opaque black sheet over the whole screen that a deliberate gesture dismisses. It is
 * deliberately not a real display timeout, and S2166 halved the reason. The reason still holds with the
 * background playback setting off: the screen pauses on ON_STOP (S0902), so letting the watch sleep
 * would stop the playback this mode exists to keep alive. With the setting on and the track playing,
 * the sleeping watch keeps playing from the service, and what this sheet is left doing is keeping the
 * screen reachable in one touch rather than keeping the sound alive. On an OLED watch the pixels under
 * an opaque black sheet are unlit anyway, which is why the cheaper mode was never worth swapping in.
 *
 * S2815 moved it out of the audio player: the video player takes the same mode, and one exit gesture
 * plus one TalkBack description is a shared requirement rather than a coincidence between two copies.
 *
 * S3097 took the single tap away from the exit (ADR-2/ADR-3). A sleeve brushing the glass is a single
 * tap, and it was ending the mode; it now only marks the point it landed on (S3200: with a ring that
 * slowly spreads out and fades), which tells the wearer
 * the watch is awake and the sheet is deliberate. The exit is a double tap, a long press, or the
 * hardware button - the last one through [BackHandler], because without it the button leaves the
 * screen altogether instead of lifting the sheet.
 *
 * S3098 moved it out of the player package entirely and renamed it: the navigation host raises the
 * same sheet from any ordinary screen, so an overlay named after the players would have been a false
 * name at a false address for the screen that now calls it most.
 *
 * S4020: the sheet draws no standing clock and no status line - the mode exists to spend nothing on a
 * picture, so the tap response below is the only thing it ever lights. S4139 narrowed it: the current
 * time and date are part of that tap response, above the tap point for one second, so a glance at the
 * time no longer costs lifting the sheet. They go through [WearUnitDateTimeFormatter], so the clock
 * length and date order follow the watch's unit system like every other watch clock.
 *
 * S4139: the window runs at the lowest brightness while the sheet is up and at full brightness while
 * a tap response animates; lifting the sheet restores the window brightness it found on the way in.
 *
 * S3370: the tap now also throws a pair of tapered compass sparks out of the touch point - red
 * toward geographic north, blue toward south, driven by the live heading flow collected while this
 * sheet is up; without a confident heading both sparks are white on a random azimuth chosen fresh
 * per tap. Drawing stays inside the same draw pass and only while the animation is active.
 */
@Composable
internal fun WearDimOverlay(onExit: () -> Unit) {
    val exitDesc = stringResource(R.string.wear_screen_off_exit_hint)
    var tapMark by remember { mutableStateOf<Offset?>(null) }
    // Starts finished so nothing is drawn before the first tap. animateTo cancels a running animation,
    // so a tap during a ring restarts it from the new point instead of stacking a second ring.
    val tapProgress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // The callers pass a method reference, which is a fresh instance on every recomposition, and the
    // player recomposes about once a second while the track runs. Keying the gesture detector on it
    // would restart the detector mid-gesture and swallow the second half of a double tap.
    val currentExit by rememberUpdatedState(onExit)
    // S3370: the heading flow is collected only while this sheet is composed, so the sensor
    // listener lives exactly as long as the dim screen - the OLED economy is untouched.
    val context = LocalContext.current
    val observeHeading: ObserveHeadingUseCase = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WearDimOverlayEntryPoint::class.java
        ).heading()
    }
    val heading by remember(context) { observeHeading() }
        .collectAsStateWithLifecycle(initialValue = null)
    // Azimuth captured at tap time, already flipped so it aims at geographic north on screen.
    var sparkNorthAzimuthDegrees by remember { mutableFloatStateOf(0f) }
    val stripePath = remember { Path() }
    val textMeasurer = rememberTextMeasurer()
    // Captured at tap time: the clock shows the moment of the glance, not a ticking value per frame.
    var tapClock by remember { mutableStateOf<Pair<String, String>?>(null) }
    val clockFormatter = LocalWearDateTimeFormatter.current
    val unitSystem = LocalWearUnitSystem.current
    val clockStyles = MaterialTheme.typography.let { type ->
        type.display3.copy(color = Color.White) to type.caption1.copy(color = Color.White)
    }

    BackHandler { currentExit() }
    DimSheetBrightnessEffect(context = context, isTapResponseRunning = { tapProgress.value < 1f })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // The sheet is modal, so it takes the top of the focus stack for as long as it is up:
            // without it a crown turn still reaches the screen dimmed underneath and scrolls or seeks
            // something the wearer cannot see, and focus returns to that screen when this one leaves.
            .rotaryActionSwallow()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { point ->
                        tapMark = point
                        tapClock = formatTapClock(clockFormatter, unitSystem)
                        Timber.d("S4139: dim sheet tap shows clock")
                        val azimuth = heading?.takeIf { it.isTrustworthy }?.azimuthDegrees
                            ?: (Random.nextFloat() * FULL_CIRCLE_DEGREES)
                        sparkNorthAzimuthDegrees = (FULL_CIRCLE_DEGREES - azimuth) % FULL_CIRCLE_DEGREES
                        scope.launch {
                            tapProgress.snapTo(0f)
                            tapProgress.animateTo(
                                1f,
                                tween(TAP_MARK_DURATION_MS, easing = LinearOutSlowInEasing)
                            )
                        }
                    },
                    onDoubleTap = { currentExit() },
                    onLongPress = { currentExit() }
                )
            }
            .drawBehind {
                val center = tapMark
                val progress = tapProgress.value
                if (center != null && progress < 1f) {
                    drawCircle(
                        color = Color.White,
                        radius = lerp(TAP_MARK_START_RADIUS, TAP_MARK_END_RADIUS, progress).toPx(),
                        center = center,
                        alpha = 1f - progress,
                        style = Stroke(width = TAP_MARK_STROKE.toPx())
                    )
                    val sparkProgress = progress * TAP_MARK_DURATION_MS / SPARK_DURATION_MS
                    if (sparkProgress < 1f) {
                        drawSparkPair(stripePath, center, sparkNorthAzimuthDegrees, sparkProgress)
                    }
                    val clock = tapClock
                    if (clock != null && progress * TAP_MARK_DURATION_MS < TAP_CLOCK_VISIBLE_MS) {
                        drawTapClock(textMeasurer, clock, clockStyles, center)
                    }
                }
            }
            .semantics { contentDescription = exitDesc }
    )
}

private fun DrawScope.drawSparkPair(
    stripePath: Path,
    center: Offset,
    northAzimuthDegrees: Float,
    progress: Float,
) {
    val north = TapResponseGeometry.directionUnitVector(northAzimuthDegrees)
    drawSparkStripe(stripePath, center, north, progress, SPARK_NORTH_COLOR)
    val south = TapResponseGeometry.oppositeDirection(north)
    drawSparkStripe(stripePath, center, south, progress, SPARK_SOUTH_COLOR)
}

private fun DrawScope.drawSparkStripe(
    stripePath: Path,
    center: Offset,
    direction: Pair<Float, Float>,
    progress: Float,
    color: Color,
) {
    val (dirX, dirY) = direction
    val along = Offset(dirX, dirY)
    val normal = Offset(-dirY, dirX)
    val baseOffset = SPARK_TRAVEL_DP.toPx() * progress
    val length = SPARK_LENGTH_DP.toPx()
    val peakHalfWidth = SPARK_PEAK_WIDTH_DP.toPx() * SPARK_STRIPE_HALF
    stripePath.reset()
    for (i in 0..SPARK_PROFILE_STEPS) {
        val t = i / SPARK_PROFILE_STEPS.toFloat()
        val halfWidth = peakHalfWidth * TapResponseGeometry.widthProfile(t)
        val point = center + along * (baseOffset + length * t) + normal * halfWidth
        if (i == 0) stripePath.moveTo(point.x, point.y) else stripePath.lineTo(point.x, point.y)
    }
    for (i in SPARK_PROFILE_STEPS downTo 0) {
        val t = i / SPARK_PROFILE_STEPS.toFloat()
        val halfWidth = peakHalfWidth * TapResponseGeometry.widthProfile(t)
        val point = center + along * (baseOffset + length * t) - normal * halfWidth
        stripePath.lineTo(point.x, point.y)
    }
    stripePath.close()
    drawPath(stripePath, color, alpha = 1f - progress)
}

/**
 * Holds the window at the lowest brightness while the sheet is dark and at full brightness while a tap
 * response runs, then gives back the value it found. Only this window's override is touched, never the
 * watch's own brightness setting, so nothing outlives the window if the process dies mid-sheet.
 */
@Composable
private fun DimSheetBrightnessEffect(context: Context, isTapResponseRunning: () -> Boolean) {
    LaunchedEffect(context) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        val brightnessBefore = window.attributes.screenBrightness
        try {
            snapshotFlow { isTapResponseRunning() }
                .distinctUntilChanged()
                .collect { lit ->
                    Timber.d("S4139: dim sheet brightness lit=$lit")
                    window.setScreenBrightness(
                        if (lit) MAX_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
                    )
                }
        } finally {
            window.setScreenBrightness(brightnessBefore)
        }
    }
}

private fun Window.setScreenBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}

/** Time on the first line, weekday and date on the second, in the unit system every watch clock obeys. */
private fun formatTapClock(formatter: WearUnitDateTimeFormatter, system: UnitSystem): Pair<String, String> {
    val now = System.currentTimeMillis()
    val date = formatter.formatWeekday(now) + " " + formatter.formatDate(now, system)
    return formatter.formatTime(now, system) to date
}

private fun DrawScope.drawTapClock(
    textMeasurer: TextMeasurer,
    clock: Pair<String, String>,
    styles: Pair<TextStyle, TextStyle>,
    tap: Offset,
) {
    val timeLayout = textMeasurer.measure(clock.first, styles.first)
    val dateLayout = textMeasurer.measure(clock.second, styles.second)
    val blockHeight = (timeLayout.size.height + dateLayout.size.height).toFloat()
    val gap = TAP_CLOCK_GAP.toPx()
    val above = tap.y - gap - blockHeight
    // A tap near the top edge leaves no room above it, so the clock moves below the finger instead.
    val top = if (above >= 0f) above else (tap.y + gap).coerceAtMost(size.height - blockHeight)
    drawText(timeLayout, topLeft = Offset(centeredLeft(tap.x, timeLayout.size.width), top))
    drawText(
        dateLayout,
        topLeft = Offset(centeredLeft(tap.x, dateLayout.size.width), top + timeLayout.size.height)
    )
}

private fun DrawScope.centeredLeft(x: Float, width: Int): Float =
    (x - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
