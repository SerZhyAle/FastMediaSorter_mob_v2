package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
 * A compact caption-value pair for watch-sized information screens.
 *
 * Both columns meet at the row centre: the caption is right-aligned against that boundary and the
 * value begins there. The value owns its half of the row and wraps there, so a device-provided
 * value cannot separate itself from the caption that explains it. One merged semantic node keeps
 * the pair meaningful to TalkBack as well.
 *
 * Long-press copies the value to the clipboard with haptic confirmation (S2775). On a watch the
 * haptic is the primary feedback: there is no Snackbar host and a toast would cover the report.
 *
 * @param accentColor when non-null the value text is drawn in this colour instead of the theme
 * default, used by the system-information report to highlight anomalous health readings (S2775).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearInformationRow(
    @StringRes labelRes: Int,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accessibilitySuffix: String? = null,
    accentColor: Color? = null
) {
    val label = stringResource(labelRes)
    val description = listOfNotNull("$label: $value", accessibilitySuffix).joinToString(". ")
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val interactionModifier = modifier.combinedClickable(
        onClick = onClick ?: {},
        onLongClick = {
            clipboard.setText(AnnotatedString(value))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

    Row(
        modifier = interactionModifier
            .fillMaxWidth()
            .padding(vertical = INFORMATION_ROW_VERTICAL_PADDING)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(INFORMATION_ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = accentColor ?: Color.Unspecified,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}
