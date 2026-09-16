package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R

// Declared as consts because detekt's MagicNumber is active on this module's main sources and
// exempts a constant declaration but not a property one.
private const val AFFORDANCE_TOUCH_TARGET_DP = 40
private const val AFFORDANCE_GLYPH_DP = 24
private val AFFORDANCE_EDGE_SHIFT = 6.dp
private const val END_RIM_RESERVATION_DP = 8

/**
 * S3098: the band along the RIGHT rim that a floating control must leave alone, unlike the left one.
 *
 * It is sized to the permanent occupant - the volume bar Browse pins at this very height, 4 dp wide
 * with 4 dp of edge padding (S2477) - and deliberately not to the further one. Wear's own position
 * indicator sits about 11 to 15 dp in from the glass on a scrollable screen, and a reservation deep
 * enough to clear it put the mark on the content band instead: measured on the Galaxy Watch 7 on
 * 2026-09-16, 16 dp laid the moon over a grid tile, while 8 dp left the content untouched and cost
 * only the crescent's outer horn grazing an indicator that is drawn while scrolling and fades after.
 */
val WearEndRimReservation: Dp = END_RIM_RESERVATION_DP.dp

/**
 * Size of the control a caller has to place. Published because the edge inset that puts it against
 * the rim is a function of its height, so a caller that guesses the height mis-places it (S2472).
 */
val WearBackAffordanceSize = AFFORDANCE_TOUCH_TARGET_DP.dp

/** The left inset shared by every floating navigation affordance. */
@Composable
fun wearBackAffordanceInset(): Dp =
    (wearSideBandInset(WearBackAffordanceSize) - AFFORDANCE_EDGE_SHIFT).coerceAtLeast(0.dp)

/**
 * What the affordance does on the screen it stands on; it decides sign, announcement and meaning
 * together, so no caller can pair the wrong glyph with the wrong action (S2472 ADR-1).
 */
enum class WearBackAffordanceRole {
    /** One screen back on the navigation stack: a small left-pointing arrow. */
    Back,

    /** Close the app from its home screen: a cross. */
    Close,

    /** Send the app to the background with sound alive: the phone's double chevron down (S0759). */
    Minimize
}

/**
 * The one back/close/minimize control of the watch app (S2472).
 *
 * The owner ruled the placement: hard against the left rim, vertically centered - the caller owns
 * the position and must align this at `Alignment.CenterStart` with leading padding from
 * [wearBackAffordanceInset], because only the caller knows its own container. Not [wearRingInset], which
 * is a corner's clearance and puts the control a seventh of the display inwards, on top of the
 * content band rather than in the free ring beside it (S2472, owner report 2026-09-03). The
 * left-middle band is where a round display's side chord is widest, so the sign survives out there
 * at full size while the list keeps its own inset untouched.
 *
 * Two sizes, and they are not the same thing: the touch target is what a finger hits, the glyph is
 * what an eye reads. The owner asked for a small but noticeable mark, which is a small glyph, never
 * a small target. They are aligned at the START of the box rather than at its centre for the same
 * reason: centring spends half the difference between them - 8 dp - pushing the mark back inwards,
 * which is the visible half of what the owner reported. The finger keeps the full target; it simply
 * grows inwards, where there is nothing at this height, instead of outwards past the glass.
 *
 * The tint uses the shared secondary accent so the navigation mark stays recognisable at a glance
 * without becoming a filled primary action.
 */
@Composable
fun WearBackAffordance(
    role: WearBackAffordanceRole,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .size(WearBackAffordanceSize)
            .nonSwallowingClickable(onClick = {
                onClick()
            })
    ) {
        when (role) {
            WearBackAffordanceRole.Back -> VectorGlyph(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                labelRes = R.string.wear_navigate_back
            )
            WearBackAffordanceRole.Close -> VectorGlyph(
                icon = Icons.Filled.Close,
                labelRes = R.string.wear_close_app
            )
            WearBackAffordanceRole.Minimize -> Icon(
                painter = painterResource(R.drawable.ic_double_arrow_down),
                contentDescription = stringResource(R.string.wear_minimize_to_background),
                tint = MaterialTheme.colors.secondary,
                modifier = Modifier.size(AFFORDANCE_GLYPH_DP.dp)
            )
        }
    }
}

/**
 * S3098: the screen-off command, standing at the opposite edge of the same box as [WearBackAffordance]
 * and on exactly the routes that draw it. The owner named those screens by the mark already on them -
 * the back arrow at the middle of the left rim - so this one mirrors it rather than declaring a route
 * list of its own, which would drift apart from that predicate at the first new screen.
 *
 * The glyph is aligned at the END of the box for the mirrored reason the navigation mark is aligned at
 * its start: centring spends half the 16 dp between the 40 dp touch target and the 24 dp glyph pushing
 * the mark inwards, away from the rim the owner asked it to stand against. The finger keeps the full
 * target, which grows inwards where there is nothing at this height.
 */
@Composable
fun WearScreenOffAffordance(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier
            .size(WearBackAffordanceSize)
            .nonSwallowingClickable(onClick = {
                onClick()
            })
    ) {
        VectorGlyph(icon = Icons.Filled.DarkMode, labelRes = R.string.wear_screen_off)
    }
}

/**
 * Detects clicks without consuming pointer down or horizontal drag events, allowing system back
 * swipe gestures (swipe-to-dismiss) starting over this control to pass through to parent containers (S2472).
 */
internal fun Modifier.nonSwallowingClickable(
    onClick: () -> Unit,
    role: Role = Role.Button
): Modifier = this
    .pointerInput(onClick) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = false)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Main)
            if (up != null && !up.isConsumed) {
                up.consume()
                onClick()
            }
        }
    }
    .semantics(mergeDescendants = true) {
        this.role = role
        onClick {
            onClick()
            true
        }
    }

@Composable
private fun VectorGlyph(icon: ImageVector, @StringRes labelRes: Int) {
    Icon(
        imageVector = icon,
        contentDescription = stringResource(labelRes),
        tint = MaterialTheme.colors.secondary,
        modifier = Modifier.size(AFFORDANCE_GLYPH_DP.dp)
    )
}
