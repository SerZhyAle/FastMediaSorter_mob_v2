package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState

/**
 * S1966: the menu's callbacks travel as one object rather than four parameters, the shape this
 * module already uses for a composable with a handful of actions.
 */
data class GameMenuActions(
    val onSkipTurn: () -> Unit,
    val onRestartLevel: () -> Unit,
    val onNewGame: () -> Unit,
    val onOpenRules: () -> Unit,
    val onExit: () -> Unit,
    val onDismiss: () -> Unit
)

/** The rows the menu offers, in the order they are drawn. */
private enum class GameMenuEntry { SKIP_TURN, RESTART_LEVEL, NEW_GAME, RULES, EXIT, CLOSE }

/**
 * Everything the board itself does not carry, opened by a long press on it.
 *
 * An overlay rather than a navigation destination, so the watch's dismiss gesture still leaves the
 * game rather than closing the menu - which is exactly why the close row exists. Without it the only
 * ways out of the menu would be its three actions, and one of those exits the game.
 *
 * A [WearListColumn] rather than a plain column: on round glass an unscaled outer row runs off
 * the edge, and the scaling list is what keeps the first and last rows tappable.
 */
@Composable
fun GameMenuOverlay(actions: GameMenuActions, modifier: Modifier = Modifier) {
    val listState = rememberWearListState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            items(GameMenuEntry.entries.size) { index ->
                val entry = GameMenuEntry.entries[index]
                Chip(
                    onClick = { actions.run(entry) },
                    colors = ChipDefaults.secondaryChipColors(),
                    label = { Text(text = stringResource(labelResFor(entry))) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Every action closes the menu behind it, so the player is returned to the board rather than left
 * looking at a list that has already been acted on. The close row does nothing else.
 */
private fun GameMenuActions.run(entry: GameMenuEntry) {
    when (entry) {
        GameMenuEntry.SKIP_TURN -> onSkipTurn()
        GameMenuEntry.RESTART_LEVEL -> onRestartLevel()
        GameMenuEntry.NEW_GAME -> onNewGame()
        GameMenuEntry.RULES -> onOpenRules()
        GameMenuEntry.EXIT -> onExit()
        GameMenuEntry.CLOSE -> Unit
    }
    onDismiss()
}

private fun labelResFor(entry: GameMenuEntry): Int = when (entry) {
    GameMenuEntry.SKIP_TURN -> R.string.wear_game_menu_skip_turn
    GameMenuEntry.RESTART_LEVEL -> R.string.wear_game_menu_restart_level
    GameMenuEntry.NEW_GAME -> R.string.wear_game_menu_new_game
    GameMenuEntry.RULES -> R.string.wear_game_menu_rules
    GameMenuEntry.EXIT -> R.string.wear_game_menu_exit
    GameMenuEntry.CLOSE -> R.string.wear_game_menu_close
}
