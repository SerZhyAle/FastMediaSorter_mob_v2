package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import timber.log.Timber

@Composable
internal fun CenteredGridRow(
    columns: Int,
    itemCount: Int,
    gap: Dp,
    content: @Composable RowScope.() -> Unit
) {
    Timber.d("S2525: CenteredGridRow columns=%d items=%d", columns, itemCount)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val cellWidth = (maxWidth - gap * (columns - 1)) / columns
        val rowWidth = cellWidth * itemCount + gap * (itemCount - 1)
        Row(
            modifier = Modifier.width(rowWidth),
            horizontalArrangement = Arrangement.spacedBy(gap),
            content = content
        )
    }
}
