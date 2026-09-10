package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme

private val DIVIDER_VERTICAL_PADDING = 4.dp
private const val DIVIDER_ALPHA = 0.3f

/**
 * The line that separates two blocks of a watch report, shared by every screen that reports rather
 * than configures (S2805).
 *
 * Extracted from the system-information screen so the Network Monitor can be read as the same
 * report: two screens drawing their own divider drift apart at the first theme change, and the
 * owner reads them one after the other.
 */
@Composable
fun WearReportDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DIVIDER_VERTICAL_PADDING)
            .height(1.dp)
            .background(MaterialTheme.colors.onSurfaceVariant.copy(alpha = DIVIDER_ALPHA))
    )
}
