package com.sza.fastmediasorter.wear.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.wear.R
import kotlinx.coroutines.delay

/** How long the acknowledgement mark stays on the glass after a single tap. */
private const val TAP_MARK_VISIBLE_MS = 600L

/** Radius of the acknowledgement mark - large enough to find, small enough to leave the glass dark. */
private val TAP_MARK_RADIUS = 6.dp

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
 * tap, and it was ending the mode; it now only marks the point it landed on, which tells the wearer
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
    // The callers pass a method reference, which is a fresh instance on every recomposition, and the
    // player recomposes about once a second while the track runs. Keying the gesture detector on it
    // would restart the detector mid-gesture and swallow the second half of a double tap.
    val currentExit by rememberUpdatedState(onExit)

    BackHandler { currentExit() }

    LaunchedEffect(tapMark) {
        if (tapMark != null) {
            delay(TAP_MARK_VISIBLE_MS)
            tapMark = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapMark = it },
                    onDoubleTap = { currentExit() },
                    onLongPress = { currentExit() }
                )
            }
            .drawBehind {
                tapMark?.let { center ->
                    drawCircle(color = Color.White, radius = TAP_MARK_RADIUS.toPx(), center = center)
                }
            }
            .semantics { contentDescription = exitDesc }
    )
}
