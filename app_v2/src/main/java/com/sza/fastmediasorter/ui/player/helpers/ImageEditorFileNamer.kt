package com.sza.fastmediasorter.ui.player.helpers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    private const val DEFAULT_EXT = "jpg"

    /**
     * @param baseName  Filename without extension and without trailing dot.
     * @param ext       Extension without leading dot (e.g. "jpg", "png").
     * @param operation One of [CROP], [COMPRESS], [DRAW].
     */
    fun buildName(baseName: String, ext: String, operation: String): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd-HHmm"))
        return "${baseName}_${operation}-${timestamp}.${ext.ifBlank { DEFAULT_EXT }}"
    }

    /**
     * Guarantees [name] ends with a real extension. Presence is detected via the substring after
     * the last dot, so a bare trailing dot ("foo.") counts as MISSING - unlike `contains('.')`,
     * which wrongly treats it as present. The trailing dot is stripped before [fallbackExt] is
     * appended. A genuine extension (including a multi-dot name like "a.b.gz") is kept untouched.
     */
    fun ensureExtension(name: String, fallbackExt: String = DEFAULT_EXT): String {
        if (name.substringAfterLast('.', "").isNotBlank()) return name
        return "${name.trimEnd('.')}.$fallbackExt"
    }
}
