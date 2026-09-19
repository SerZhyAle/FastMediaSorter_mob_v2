package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.game.GameStats
import com.sza.fastmediasorter.wear.ui.common.WearCaptionScale
import com.sza.fastmediasorter.wear.ui.common.wearBelowSquareBand
import timber.log.Timber

/** Clearance from the glass for the row as a whole; the counters inside it are laid out as one. */
private val COUNTER_EDGE_PADDING = 2.dp

/** Wide enough to read the three numbers apart, narrow enough to keep the row on the bottom chord. */
private val COUNTER_SPACING = 8.dp

/**
 * Line box of one counter as a multiple of its font size. Only the band's placement reads it: the row
 * itself keeps its measured height, so an underestimate costs an overlap with the board, never a cut.
 */
private const val COUNTER_LINE_HEIGHT_FACTOR = 1.4f

/** Keeps the counters below the board without letting them fall out of contrast (S2522). */
private const val COUNTER_TEXT_ALPHA = 0.7f

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
 *
 * S3189: that chord is the one at the board's bottom edge, not at the display's. In the STORE view,
 * which is the only one the Play build draws, the row stands directly beneath the board and is bounded
 * by the glass at its own bottom edge; the ORIGINAL view keeps it at the bottom of the display.
 */
@Composable
fun GameStatsRing(stats: GameStats, levelNumber: Int, modifier: Modifier = Modifier) {
    val lineHeight = with(LocalDensity.current) { (WearCaptionScale.Floor * COUNTER_LINE_HEIGHT_FACTOR).toDp() }
    val placement = wearBelowSquareBand(lineHeight + COUNTER_EDGE_PADDING * 2)
    LaunchedEffect(placement) {
        Timber.d("S3189: game counter band bottomOffset=${placement.bottomOffset} maxWidth=${placement.maxWidth}")
    }
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(COUNTER_SPACING),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = placement.bottomOffset)
                .widthIn(max = placement.maxWidth)
                .padding(COUNTER_EDGE_PADDING)
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
        // S2522: the game pins a black container, so the counter dims the scaffold's content colour
        // instead of reading `onSurfaceVariant`, which a light scheme makes near-black on black.
        color = LocalContentColor.current.copy(alpha = COUNTER_TEXT_ALPHA),
        maxLines = 1,
        // The three counters share one chord of the circle, so a scaled-up number has to give way
        // inside its own share rather than be cut where the row runs out (S2755).
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = description }
    )
}
