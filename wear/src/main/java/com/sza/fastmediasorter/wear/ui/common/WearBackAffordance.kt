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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R

// Declared as consts because detekt's MagicNumber is active on this module's main sources and
// exempts a constant declaration but not a property one.
private const val AFFORDANCE_TOUCH_TARGET_DP = 40
private const val AFFORDANCE_GLYPH_DP = 24

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
 * The owner ruled the placement: far left, vertically centered - the caller owns the position and
 * must align this at `Alignment.CenterStart` with leading padding from [wearRingInset], because
 * only the caller knows its own container; the left-middle band is where a round display's side
 * chord is widest, so the sign survives there at full size.
 *
 * Two sizes, and they are not the same thing: the touch target is what a finger hits, the glyph is
 * what an eye reads. The owner asked for a small but noticeable mark, which is a small glyph, never
 * a small target.
 *
 * The tint stays the neutral control colour rather than the primary highlight: an affordance that
 * is always present must not outrank the content of whatever screen it is drawn over.
 */
@Composable
fun WearBackAffordance(
    role: WearBackAffordanceRole,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AFFORDANCE_TOUCH_TARGET_DP.dp)
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
                tint = MaterialTheme.colors.onSurface,
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
        tint = MaterialTheme.colors.onSurface,
        modifier = Modifier.size(AFFORDANCE_GLYPH_DP.dp)
    )
}
