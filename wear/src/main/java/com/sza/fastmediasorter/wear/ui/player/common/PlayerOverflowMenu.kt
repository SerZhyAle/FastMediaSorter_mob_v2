package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

/**
 * The menu behind the players' "more" button, holding the commands their rows cannot show.
 *
 * S2766: below the compact-screen breakpoint a player draws three primary and two secondary
 * commands, so the playback mode, the mark, the pin, the scale mode and the screen-off command have
 * to open from somewhere - a command that leaves a row without a reachable container has not moved,
 * it has disappeared.
 *
 * The menu renders the list it is handed and knows nothing about which player opened it, so a
 * screen's own command set stays that screen's business. S3118: the video player appends the file
 * operations to that list instead of opening the file-action dialog behind one entry, so one opening
 * reaches a rename; the set it appends is still the capability policy's answer, read through
 * [rememberPlayerFileActionEntries], never a list the screen composed.
 */
@Composable
fun PlayerOverflowMenu(
    actions: List<WearAction>,
    onDismiss: () -> Unit
) {
    val backLabel = stringResource(R.string.wear_navigate_back)
    // Built here rather than by each screen: three players hand this menu their own list, and a back
    // entry added per screen is three entries that drift apart. First row, not last - the outward rows
    // of a round screen are the easiest to mis-tap, and the last row is where the destructive file
    // operations end, so "leave" must not sit beside "delete".
    val backEntry = WearAction(
        label = backLabel,
        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        onClick = {
            onDismiss()
        }
    )
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val menuScrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize()) {
            WearActionColumn(
                actions = listOf(backEntry) + actions,
                // S3118: the backdrop is the menu's cancel, the same answer WearActionColumn already
                // gives the file-action menu - without it a tap past the buttons did nothing.
                onDismiss = onDismiss,
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

/**
 * The same entry, closing the menu before it runs.
 *
 * S3118: the file operations arrive already built, because their labels, icons and order are the
 * file-action dialog's answer - this wraps that entry rather than rebuilding it, so a command still
 * never runs while the dialog that offered it is on the glass.
 */
internal fun WearAction.closingWith(onDismiss: () -> Unit): WearAction {
    val run = onClick
    return copy(
        onClick = {
            onDismiss()
            run()
        }
    )
}

/**
 * S2802: an entry that runs its command and leaves the menu up.
 *
 * Only for a command that cycles a value in place: the playback mode has three settings behind one
 * button, so a menu that closes on the first tap makes the third setting cost three openings. A
 * command that opens a dialog of its own must keep using [playerMenuAction] - this one would draw
 * that dialog under the menu.
 */
internal fun playerMenuCycleAction(
    label: String,
    icon: ImageVector,
    onRun: () -> Unit
): WearAction = WearAction(
    label = label,
    icon = { Icon(imageVector = icon, contentDescription = null) },
    onClick = {
        onRun()
    }
)
