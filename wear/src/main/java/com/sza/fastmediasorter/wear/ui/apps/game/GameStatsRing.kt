package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.game.GameStats
import com.sza.fastmediasorter.wear.ui.common.WearCaptionScale

/** Clearance from the glass for the row as a whole; the counters inside it are laid out as one. */
private val COUNTER_EDGE_PADDING = 2.dp

/** Wide enough to read the three numbers apart, narrow enough to keep the row on the bottom chord. */
private val COUNTER_SPACING = 8.dp

/**
 * The score, the level and the turn count drawn as one row in the bottom segment of the ring.
 *
 * Bare numbers, no captions: the caption is what forced each counter onto a full line of its own,
 * and those two lines took a third of the board's area (strategic §1). What the number means is
 * carried in its content description instead, so a screen reader still reads "Level: 4" while the
 * glass shows only the digit.
 *
 * All three sit in the bottom segment rather than at the bottom, left and right midpoints they held
 * until S2553. The left and right midpoints are the only two places on this screen where a control
 * stands clear of the square board, and both are now spent on the back and skip-turn affordances; a
 * counter left at either would sit under a button. The bottom chord is about 0.71 of the diameter,
 * which three short numbers fit across with room to spare.
 */
@Composable
fun GameStatsRing(stats: GameStats, levelNumber: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(COUNTER_SPACING),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.BottomCenter).padding(COUNTER_EDGE_PADDING)
        ) {
            Counter(
                value = stats.score,
                description = stringResource(R.string.wear_game_score_description, stats.score)
            )
            Counter(
                value = levelNumber,
                description = stringResource(R.string.wear_game_level_description, levelNumber)
            )
            Counter(
                value = stats.turns,
                description = stringResource(R.string.wear_game_turns_description, stats.turns)
            )
        }
    }
}

@Composable
private fun Counter(value: Int, description: String, modifier: Modifier = Modifier) {
    Text(
        text = value.toString(),
        style = MaterialTheme.typography.caption2.copy(fontSize = WearCaptionScale.Floor),
        color = MaterialTheme.colors.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = description }
    )
}
