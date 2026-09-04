package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearAction
import com.sza.fastmediasorter.wear.ui.common.WearActionCloud

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
 * A [WearActionCloud] inside an inscribed square rather than a list: the buttons wrap by content
 * width and stay within the display's visible bounds, keeping outer rows safe on round glass.
 */
@Composable
fun GameMenuOverlay(actions: GameMenuActions, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        val wearActions = GameMenuEntry.entries.map { entry ->
            WearAction(
                label = stringResource(labelResFor(entry)),
                icon = {
                    Icon(
                        imageVector = iconFor(entry),
                        contentDescription = null
                    )
                },
                onClick = { actions.run(entry) }
            )
        }
        WearActionCloud(actions = wearActions)
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

private fun iconFor(entry: GameMenuEntry): ImageVector = when (entry) {
    GameMenuEntry.SKIP_TURN -> Icons.Default.FastForward
    GameMenuEntry.RESTART_LEVEL -> Icons.Default.Refresh
    GameMenuEntry.NEW_GAME -> Icons.Default.Add
    GameMenuEntry.RULES -> Icons.Default.Info
    GameMenuEntry.EXIT -> Icons.AutoMirrored.Filled.ExitToApp
    GameMenuEntry.CLOSE -> Icons.Default.Close
}
