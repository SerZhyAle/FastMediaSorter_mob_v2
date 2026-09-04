package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordanceSize

// A const rather than a property because detekt's MagicNumber is active on this module's main
// sources and exempts a constant declaration but not a property one.
private const val SKIP_GLYPH_DP = 24

/**
 * The skip-turn control standing in the free band to the right of the board (S2553).
 *
 * Deliberately the mirror of `WearBackAffordance`: the same touch target, the same glyph size and
 * the same secondary tint, so the two marks flanking the board read as one pair rather than as a
 * navigation sign and a game button. The glyph is aligned at the END of its box for the reason the
 * back affordance aligns its own at the start - centring would spend half the difference between
 * target and glyph pushing the mark back over the board, while the finger keeps the full target,
 * which simply grows inwards where nothing else stands at this height.
 *
 * Skipping a turn was reachable only through the long-press menu (S2158), which costs a gesture on
 * the move the player makes most often.
 */
@Composable
fun GameSkipTurnButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier
            .size(WearBackAffordanceSize)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.Filled.FastForward,
            contentDescription = stringResource(R.string.wear_game_menu_skip_turn),
            tint = MaterialTheme.colors.secondary,
            modifier = Modifier.size(SKIP_GLYPH_DP.dp)
        )
    }
}
