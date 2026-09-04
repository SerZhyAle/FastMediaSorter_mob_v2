package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
            .clickable(role = Role.Button, onClick = onClick)
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

@Composable
private fun VectorGlyph(icon: ImageVector, @StringRes labelRes: Int) {
    Icon(
        imageVector = icon,
        contentDescription = stringResource(labelRes),
        tint = MaterialTheme.colors.secondary,
        modifier = Modifier.size(AFFORDANCE_GLYPH_DP.dp)
    )
}
