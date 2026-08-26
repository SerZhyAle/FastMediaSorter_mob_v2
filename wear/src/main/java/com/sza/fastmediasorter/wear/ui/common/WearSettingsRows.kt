package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private val ROW_GAP = GridColumnFit.DEFAULT_GAP_DP.dp

/**
 * Splits settings controls into rows of at most [columns].
 *
 * A full-width item always forms its own row. Runs of narrow items pack into rows of [columns], and
 * a trailing partial row keeps only the items it has - the row composable divides the width among
 * whatever a row actually holds, so a run of four at three columns ends in one full-width item
 * rather than a two-thirds-empty row (strategic S1949 6.3).
 *
 * Pure on purpose: no Compose types in the signature, so the packing rule is testable without a
 * device or a composition.
 */
fun packSettingsRows(
    items: List<WearSettingsItem>,
    columns: Int
): List<List<WearSettingsItem>> {
    Timber.d("S1949: packSettingsRows columns=$columns items=${items.size}")
    if (columns <= 1) {
        return items.map { listOf(it) }
    }

    val rows = mutableListOf<List<WearSettingsItem>>()
    val run = mutableListOf<WearSettingsItem>()

    fun flushRun() {
        if (run.isEmpty()) return
        rows += run.chunked(columns)
        run.clear()
    }

    for (item in items) {
        if (item.fullWidth) {
            flushRun()
            rows += listOf(item)
        } else {
            run += item
        }
    }
    flushRun()
    return rows
}

/**
 * Renders one packed row. The cells share the available width equally, so a row holding a single
 * item is a full-width control without the caller having to say so twice.
 */
@Composable
fun WearSettingsRow(
    row: List<WearSettingsItem>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP)
    ) {
        val narrow = row.size > 1
        for (item in row) {
            Box(modifier = Modifier.weight(1f)) {
                item.content(narrow)
            }
        }
    }
}
