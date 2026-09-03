package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry

/**
 * S2142: the receivers «Send to..» opens onto, drawn over the action menu rather than beside it.
 *
 * Beside [WearFileActionsDialog] rather than inside one screen's package, and for its reason: three
 * surfaces open that menu, and strategic 11 criterion 4 requires the same file to offer the same set
 * on every one of them. A copy per screen is what that criterion forbids.
 *
 * A second dialog and not a screen, which strategic 3.4 settles outright: the owner reaches it from
 * the menu, dismisses it by swiping and lands back where the file is, so no navigation point is
 * added. A [ScalingLazyColumn] for the same reason the action menu uses one - a list longer than the
 * round screen has to scroll under the crown instead of being cut off (strategic 3.2).
 *
 * A receiver the phone serves is drawn with its "through the phone" note and is never hidden while
 * the phone is out of range: ADR-3 draws the line at "does not exist", and a phone in another room
 * still exists. Hiding it would change the list's length with the room the phone is in and take away
 * the position the owner's finger remembers.
 */
@Composable
internal fun ReceiverListDialog(
    receivers: List<WearSendToReceiverEntry>,
    onPick: (WearSendToReceiverEntry) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberWearListState()
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_send_to_title),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = TITLE_GAP)
                )
            }

            items(receivers, key = { it.id }) { entry ->
                ReceiverChip(entry = entry, onClick = { onPick(entry) })
            }
        }
    }
}

/**
 * One receiver, one tap target, one spoken label.
 *
 * The "through the phone" note is read out as part of that label rather than shown only as a second
 * line, because strategic 3.4 requires it to survive being spoken - a distinction carried by layout
 * alone does not reach the owner who cannot see it.
 */
@Composable
private fun ReceiverChip(
    entry: WearSendToReceiverEntry,
    onClick: () -> Unit
) {
    val viaPhone = stringResource(R.string.wear_send_to_via_phone)
    val spoken = if (entry.servedOnWatch) entry.title else "${entry.title}, $viaPhone"
    Chip(
        onClick = onClick,
        label = { Text(text = entry.title) },
        secondaryLabel = if (entry.servedOnWatch) null else {
            { Text(text = viaPhone) }
        },
        icon = {
            Icon(
                imageVector = glyphFor(entry.iconName),
                contentDescription = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
        colors = ChipDefaults.secondaryChipColors()
    )
}

/**
 * The glyph name the phone published, resolved in this module's own icon set.
 *
 * A name rather than an image is ADR-5: the phone's `R` class does not exist here, and pushing
 * fourteen pictures through the data layer would be the project's first such machinery, built for
 * decoration. An unknown name falls back to the generic glyph on purpose - that is what lets a
 * receiver declared on the phone later appear here at once, without a matching edit on this side.
 */
private fun glyphFor(iconName: String?): ImageVector = when (iconName) {
    "Share" -> Icons.Default.Share
    "OpenInNew" -> Icons.AutoMirrored.Filled.OpenInNew
    "Watch" -> Icons.Default.Watch
    "Print" -> Icons.Default.Print
    "Email" -> Icons.Default.Email
    "EditNote" -> Icons.Default.EditNote
    "Brush" -> Icons.Default.Brush
    "ImageSearch" -> Icons.Default.ImageSearch
    "Chat" -> Icons.AutoMirrored.Filled.Chat
    "PhoneInTalk" -> Icons.Default.PhoneInTalk
    "Bolt" -> Icons.Default.Bolt
    "PhotoCamera" -> Icons.Default.PhotoCamera
    "MusicNote" -> Icons.Default.MusicNote
    else -> Icons.AutoMirrored.Filled.Send
}

private val TITLE_GAP = 8.dp
