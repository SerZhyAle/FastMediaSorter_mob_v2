package com.sza.fastmediasorter.core.util

import android.content.Context
import com.sza.fastmediasorter.R
import java.util.Locale

private const val BYTES_PER_UNIT = 1024.0

/** Below this, the exact byte count is more useful than a rounded one - cache sizes, thumbnails, tiny configs. */
private const val EXACT_BYTES_CEILING = 10240L

/**
 * Format file size to human-readable format with bytes grouped by thousands.
 *
 * Examples (en):
 * - Small files (< 10KB): "1 234 567 B" (exact bytes with space separators)
 * - Medium files: "45.67 KB", "123.45 MB"
 * - Large files: "2.34 GB"
 *
 * The unit label comes from the `unit_size_*` resources, so a locale that renders "45,67" also renders
 * "КБ" beside it instead of an English literal (S2351).
 *
 * Thresholds stay binary on purpose rather than delegating to `android.text.format.Formatter`: that one
 * switched to powers of 1000 at API 26, so the legacy flavor's API 23 floor would print a different number
 * than every other device for the same file, it cannot produce the exact grouped-byte form above, and it
 * drops all decimals once the value reaches 100.
 */
fun formatFileSize(context: Context, bytes: Long): String {
    if (bytes < EXACT_BYTES_CEILING) {
        val exact = String.format(Locale.getDefault(), "%,d", bytes).replace(',', ' ')
        return context.getString(R.string.unit_size_bytes, exact)
    }

    val kb = bytes / BYTES_PER_UNIT
    val mb = kb / BYTES_PER_UNIT
    val gb = mb / BYTES_PER_UNIT
    val (value, unitRes) = when {
        kb < BYTES_PER_UNIT -> kb to R.string.unit_size_kb
        mb < BYTES_PER_UNIT -> mb to R.string.unit_size_mb
        else -> gb to R.string.unit_size_gb
    }
    return context.getString(unitRes, String.format(Locale.getDefault(), "%.2f", value))
}
