package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private val INFORMATION_ROW_VERTICAL_PADDING = 2.dp

/**
 * What one information row's two gestures do, when either differs from the default.
 *
 * The two travel together rather than as two parameters: they are one decision - a row that owns a
 * tap is exactly the row whose clipboard text is not the pair it displays - and the row already
 * carries the number of parameters detekt allows.
 *
 * @param onClick what a tap does instead of copying. A row that declares one copies on long press.
 * @param copyText what to put in the clipboard instead of the rendered "label: value", for a row
 * whose visible value stands for content it does not itself show - a collapsed set showing its size.
 */
data class WearInformationRowGestures(
    val onClick: (() -> Unit)? = null,
    val copyText: String? = null
)

/**
 * A compact caption-value pair for watch-sized information screens.
 *
 * Both columns meet at the row centre: the caption is right-aligned against that boundary and the
 * value begins there. The value owns its half of the row and wraps there, so a device-provided
 * value cannot separate itself from the caption that explains it. One merged semantic node keeps
 * the pair meaningful to TalkBack as well.
 *
 * Vertically the two columns share a first baseline rather than a centre (S3180): a device-provided
 * value that wraps to three lines used to centre the caption against the whole block, leaving the
 * value's first line above the caption and its last line below, so the row read as two unrelated
 * blocks. The baseline also fits the single-line case better, because the caption is drawn in the
 * smaller caption2 while the value is body2.
 *
 * Copying is what a plain value tile DOES (S3108): a tile with no click of its own copies on tap,
 * because it is drawn as a pressable panel and used to answer a press with nothing at all. A tile
 * that owns a click keeps it and copies on long press instead, so no element carries two meanings
 * for the same gesture. Either way the haptic is the whole confirmation: there is no Snackbar host
 * on a watch, and a toast would cover the report the user is reading (S2775).
 *
 * What lands in the clipboard is "label: value", not the bare value - the report is pasted into a
 * message or a note, where a reading with no name attached says nothing.
 *
 * @param accentColor when non-null the value text is drawn in this colour instead of the theme
 * default, used by the system-information report to highlight anomalous health readings (S2775).
 * @param narrow when true, label and value are stacked vertically for 2-column tile cell placement (S3018).
 * @param gestures what the row's tap and long press do, when either differs from the default.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearInformationRow(
    @StringRes labelRes: Int,
    value: String,
    modifier: Modifier = Modifier,
    accessibilitySuffix: String? = null,
    accentColor: Color? = null,
    narrow: Boolean = false,
    gestures: WearInformationRowGestures = WearInformationRowGestures()
) {
    val label = stringResource(labelRes)
    val pair = "$label: $value"
    val description = listOfNotNull(pair, accessibilitySuffix).joinToString(". ")
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val copy = {
        clipboard.setText(AnnotatedString(gestures.copyText ?: pair))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val interactionModifier = modifier.combinedClickable(
        onClick = gestures.onClick ?: copy,
        onLongClick = copy
    )

    val commonModifier = interactionModifier
        .fillMaxWidth()
        .padding(vertical = INFORMATION_ROW_VERTICAL_PADDING)
        .semantics(mergeDescendants = true) { contentDescription = description }

    if (narrow) {
        Column(
            modifier = commonModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                style = MaterialTheme.typography.body2,
                color = accentColor ?: Color.Unspecified,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Row(
            modifier = commonModifier,
            horizontalArrangement = Arrangement.spacedBy(INFORMATION_ROW_VERTICAL_PADDING)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
            )
            Text(
                text = value,
                style = MaterialTheme.typography.body2,
                color = accentColor ?: Color.Unspecified,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
            )
        }
    }
}
