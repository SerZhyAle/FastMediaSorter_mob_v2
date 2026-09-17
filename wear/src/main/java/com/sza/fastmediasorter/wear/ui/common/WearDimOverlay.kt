package com.sza.fastmediasorter.wear.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.sza.fastmediasorter.wear.R
import kotlinx.coroutines.launch
import timber.log.Timber

/** How long the acknowledgement ring takes to spread out and fade after a single tap - slow on purpose. */
private const val TAP_MARK_DURATION_MS = 1400

/** The ring is born at the size the old static mark had, so the tap point is still found at once. */
private val TAP_MARK_START_RADIUS = 6.dp

/** Wide enough to read as a wave, small enough to leave most of the glass dark. */
private val TAP_MARK_END_RADIUS = 48.dp

private val TAP_MARK_STROKE = 2.dp

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

    BackHandler { currentExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { point ->
                        Timber.d("S3200: dim overlay tap ring started")
                        tapMark = point
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
                }
            }
            .semantics { contentDescription = exitDesc }
    )
}
