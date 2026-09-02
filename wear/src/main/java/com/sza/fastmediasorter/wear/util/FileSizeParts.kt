package com.sza.fastmediasorter.wear.util

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import java.util.Locale

private const val BYTES_PER_UNIT = 1024.0

/** A size already split into the number to print and the resource that names its unit. */
internal data class FileSizeParts(val value: String, @StringRes val unitRes: Int)

/**
 * Split a byte count into a locale-formatted number and the resource naming its unit.
 *
 * The unit comes back as a resource id rather than as finished text so the Compose caller can name it
 * through `stringResource`, which follows the composition's configuration. Resolving it here would need a
 * `Context`, whose configuration is the one a preview or a `CompositionLocalProvider` may be overriding,
 * and would put an Android type in front of every assertion in a module that has no Robolectric.
 *
 * Thresholds and precision are the ones this screen already showed - binary steps, one decimal, no GB
 * step: the label was the defect, the numbers were not.
 */
internal fun fileSizeParts(bytes: Long, locale: Locale = Locale.getDefault()): FileSizeParts {
    val kilobytes = bytes / BYTES_PER_UNIT
    val megabytes = kilobytes / BYTES_PER_UNIT
    if (kilobytes < 1) {
        return FileSizeParts(bytes.toString(), R.string.wear_unit_size_bytes)
    }
    val (value, unitRes) = if (megabytes >= 1) {
        megabytes to R.string.wear_unit_size_mb
    } else {
        kilobytes to R.string.wear_unit_size_kb
    }
    return FileSizeParts(String.format(locale, "%.1f", value), unitRes)
}
