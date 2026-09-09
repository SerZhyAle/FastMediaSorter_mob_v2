package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearAction
import com.sza.fastmediasorter.wear.ui.common.WearActionColumn
import timber.log.Timber

/**
 * The menu behind the players' "more" button, holding the commands their rows cannot show.
 *
 * S2766: below the compact-screen breakpoint a player draws three primary and two secondary
 * commands, so the playback mode, the mark, the pin, the scale mode and the screen-off command have
 * to open from somewhere - a command that leaves a row without a reachable container has not moved,
 * it has disappeared.
 *
 * This is deliberately NOT [com.sza.fastmediasorter.wear.ui.common.WearFileActionsDialog]. That
 * dialog renders exactly the set the file capability policy allows, and it is the single answer to
 * "what may this file be asked to do"; a playback mode inside it would make that answer wrong. The
 * file operations stay behind one entry of this menu, which opens that dialog unchanged.
 *
 * The menu renders the list it is handed and knows nothing about which player opened it, so a
 * screen's own command set stays that screen's business.
 */
@Composable
fun PlayerOverflowMenu(
    actions: List<WearAction>,
    onDismiss: () -> Unit
) {
    Timber.d("S2766: player overflow menu opened, entries=%d", actions.size)
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val menuScrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize()) {
            WearActionColumn(
                actions = actions,
                scrollState = menuScrollState,
                header = {
                    Text(
                        text = stringResource(R.string.wear_file_op_actions),
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center
                    )
                }
            )
            PositionIndicator(menuScrollState)
        }
    }
}

/**
 * One entry of the player menu.
 *
 * The entry closes the menu before running its command, so a command is never issued while the
 * dialog that offered it is still on the glass - the dialog it opens would otherwise sit under one.
 */
internal fun playerMenuAction(
    label: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    onRun: () -> Unit
): WearAction = WearAction(
    label = label,
    icon = { Icon(imageVector = icon, contentDescription = null) },
    onClick = {
        onDismiss()
        onRun()
    }
)
