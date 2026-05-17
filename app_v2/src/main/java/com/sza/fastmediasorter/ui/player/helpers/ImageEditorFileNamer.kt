package com.sza.fastmediasorter.ui.player.helpers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import timber.log.Timber

/**
 * Builds the standardised output filename for image editor operations.
 *
 * Formula: <baseName>_<operation>-<yyMMdd-HHmm>.<ext>
 *
 * Use the provided constants for [operation] to guarantee consistent labels.
 */
object ImageEditorFileNamer {

    const val CROP = "crop"
    const val COMPRESS = "compress"
    const val DRAW = "draw"

    /**
     * @param baseName  Filename without extension and without trailing dot.
     * @param ext       Extension without leading dot (e.g. "jpg", "png").
     * @param operation One of [CROP], [COMPRESS], [DRAW].
     */
    fun buildName(baseName: String, ext: String, operation: String): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd-HHmm"))
        return "${baseName}_${operation}-${timestamp}.${ext}"
    }
}
