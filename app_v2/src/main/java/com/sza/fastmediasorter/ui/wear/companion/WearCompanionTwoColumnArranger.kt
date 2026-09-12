package com.sza.fastmediasorter.ui.wear.companion

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

private val TWO_COLUMN_BREAKPOINT = 480.dp

/**
 * Arranges children in one or two columns based on available width: two columns in landscape and wide
 * portrait, one column on narrow widths. Children are placed in source order (left-to-right, then
 * top-to-bottom) and are never reordered, so the companionRowTag source-text order the parity gate
 * reads is preserved.
 */
@Composable
fun WearCompanionTwoColumnArranger(
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= TWO_COLUMN_BREAKPOINT) {
            TwoColumnLayout(content = content)
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun TwoColumnLayout(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val columnWidth = constraints.maxWidth / 2
        val placeables = measurables.map { it.measure(constraints.copy(maxWidth = columnWidth)) }
        val rowCount = (placeables.size + 1) / 2
        val rowHeights = IntArray(rowCount)
        placeables.forEachIndexed { index, placeable ->
            val row = index / 2
            if (placeable.height > rowHeights[row]) {
                rowHeights[row] = placeable.height
            }
        }
        val totalHeight = rowHeights.sumOf { it }
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            for (row in 0 until rowCount) {
                var x = 0
                for (col in 0..1) {
                    val index = row * 2 + col
                    if (index < placeables.size) {
                        placeables[index].place(x, y)
                    }
                    x += columnWidth
                }
                y += rowHeights[row]
            }
        }
    }
}
