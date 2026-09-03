package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.layout.Box
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

/**
 * One padding for all three counters: each sits at the midpoint of a free segment, and those three
 * midpoints stand the same distance from the glass, so a second value would only make them disagree.
 */
private val COUNTER_EDGE_PADDING = 2.dp

/**
 * The score, the level and the turn count drawn in the band around the board.
 *
 * Bare numbers, no captions: the caption is what forced each counter onto a full line of its own,
 * and those two lines took a third of the board's area (strategic §1). What the number means is
 * carried in its content description instead, so a screen reader still reads "Level: 4" while the
 * glass shows only the digit.
 *
 * Placed at the midpoints of the bottom, left and right segments - the corners of the band are not
 * usable, because that is where the square board reaches the glass.
 */
@Composable
fun GameStatsRing(stats: GameStats, levelNumber: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Counter(
            value = stats.score,
            description = stringResource(R.string.wear_game_score_description, stats.score),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        Counter(
            value = levelNumber,
            description = stringResource(R.string.wear_game_level_description, levelNumber),
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Counter(
            value = stats.turns,
            description = stringResource(R.string.wear_game_turns_description, stats.turns),
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun Counter(value: Int, description: String, modifier: Modifier = Modifier) {
    Text(
        text = value.toString(),
        style = MaterialTheme.typography.caption2.copy(fontSize = WearCaptionScale.Floor),
        color = MaterialTheme.colors.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .padding(COUNTER_EDGE_PADDING)
            .semantics { contentDescription = description }
    )
}
