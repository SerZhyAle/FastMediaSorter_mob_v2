package com.sza.fastmediasorter.ui.browse.transfer

private const val PERCENT_SCALE = 100L
private const val PERCENT_MAX = 100

/** Highest byte-derived percent while the operation still runs - only a terminal event may read 100. */
private const val PERCENT_RUNNING_CEILING = 99

/**
 * Byte-derived completion percent, or null when the operation total is not known yet.
 *
 * Callers that must render a number use [transferOverallPercent]; the nullable form exists for the
 * progress dialog, which hides its percent label and ETA entirely while the total is unknown rather
 * than showing a file-count figure that would disagree with the ETA.
 */
fun transferBytePercentOrNull(completedOperationBytes: Long, totalOperationBytes: Long): Int? =
    if (totalOperationBytes > 0L) {
        (completedOperationBytes * PERCENT_SCALE / totalOperationBytes)
            .toInt()
            .coerceIn(0, PERCENT_RUNNING_CEILING)
    } else {
        null
    }

/**
 * Overall completion percent of a running copy/move - byte-derived when the total is known, file
 * count otherwise.
 *
 * Single source of truth for the modal progress dialog and the S1227 background strip. The two show
 * the same operation at different moments and must never disagree, so the formula lives in one
 * function rather than being spelled out at each site. S1225 replaces the body when the unified
 * progress component lands.
 */
fun transferOverallPercent(
    completedOperationBytes: Long,
    totalOperationBytes: Long,
    currentIndex: Int,
    totalFiles: Int,
): Int = transferBytePercentOrNull(completedOperationBytes, totalOperationBytes)
    ?: (currentIndex * PERCENT_MAX / totalFiles.coerceAtLeast(1))
