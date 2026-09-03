package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private val INFORMATION_ROW_VERTICAL_PADDING = 2.dp

/**
 * A compact caption-value pair for watch-sized information screens.
 *
 * The value owns its half of the row and wraps there, so a device-provided value cannot separate
 * itself from the caption that explains it. One merged semantic node keeps the pair meaningful to
 * TalkBack as well.
 */
@Composable
fun WearInformationRow(
    @StringRes labelRes: Int,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accessibilitySuffix: String? = null
) {
    val label = stringResource(labelRes)
    val description = listOfNotNull("$label: $value", accessibilitySuffix).joinToString(". ")
    val interactionModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)

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
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
