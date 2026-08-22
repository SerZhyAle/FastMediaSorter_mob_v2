package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.game.GameDirection
import com.sza.fastmediasorter.wear.domain.game.GameStatus
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import kotlin.math.abs

private val SCREEN_PADDING = 6.dp
private val HEADER_SPACING = 2.dp

/** Below this the gesture was a tap or a tremor, not a swipe, and no move is made. */
private val MIN_SWIPE_TRAVEL = 16.dp

/**
 * The game: a board filling the screen, moved by swiping across it.
 *
 * @param navController the route's host controller. The game itself never navigates: the owner ruled
 * on 2026-08-19 that it is played by swipes across the board with no on-screen controls, and leaving
 * is the platform's edge swipe, which this screen deliberately leaves unclaimed.
 */
// navController is unused on purpose: the route contract hands it to every screen, and the game
// deliberately navigates nowhere - leaving it is the platform's edge swipe (owner, 2026-08-19).
@Suppress("UnusedParameter")
@Composable
fun GameScreen(
    navController: NavController,
    viewModel: GameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WearScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(SCREEN_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HEADER_SPACING, Alignment.CenterVertically)
        ) {
            GameHeader(uiState)
            val level = uiState.level
            if (level != null) {
                GameBoardCanvas(
                    level = level,
                    contentDescription = stringResource(R.string.wear_game_board_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .swipeToMove { direction -> viewModel.move(direction) }
                )
            }
            if (uiState.status != GameStatus.PLAYING) {
                GameOutcomeChip(uiState.status) { viewModel.restart() }
            }
        }
    }
}

@Composable
private fun GameHeader(uiState: GameUiState) {
    Text(
        text = "${stringResource(R.string.wear_game_score)} ${uiState.stats.score}",
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onBackground,
        textAlign = TextAlign.Center
    )
    Text(
        text = "${stringResource(R.string.wear_game_turns)} ${uiState.stats.turns}",
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun GameOutcomeChip(status: GameStatus, onAct: () -> Unit) {
    val won = status == GameStatus.LEVEL_WON
    val outcome = if (won) R.string.wear_game_level_completed else R.string.wear_game_level_lost
    val action = if (won) R.string.wear_game_continue else R.string.wear_game_restart
    Chip(
        onClick = onAct,
        colors = ChipDefaults.primaryChipColors(),
        label = { Text(text = stringResource(action)) },
        secondaryLabel = { Text(text = stringResource(outcome)) },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * One move per gesture, decided by the axis the finger travelled furthest along.
 *
 * The drag is consumed: without that the platform reads a horizontal swipe as "dismiss this screen"
 * and the player leaves the game instead of moving left. Only drags that started on the board are
 * claimed, so the edge swipe that leaves the game still belongs to the platform.
 */
private fun Modifier.swipeToMove(onMove: (GameDirection) -> Unit): Modifier = pointerInput(onMove) {
    val threshold = MIN_SWIPE_TRAVEL.toPx()
    var travel = Offset.Zero
    detectDragGestures(
        onDragStart = { travel = Offset.Zero },
        onDragCancel = { travel = Offset.Zero },
        onDragEnd = { directionOf(travel, threshold)?.let(onMove) },
        onDrag = { change, amount ->
            change.consume()
            travel += amount
        }
    )
}

private fun directionOf(travel: Offset, threshold: Float): GameDirection? {
    val horizontal = abs(travel.x) >= abs(travel.y)
    val distance = if (horizontal) abs(travel.x) else abs(travel.y)
    return when {
        distance < threshold -> null
        horizontal && travel.x > 0 -> GameDirection.RIGHT
        horizontal -> GameDirection.LEFT
        travel.y > 0 -> GameDirection.DOWN
        else -> GameDirection.UP
    }
}
