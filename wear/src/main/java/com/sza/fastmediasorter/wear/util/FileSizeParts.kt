package com.sza.fastmediasorter.wear.util

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import timber.log.Timber
import java.util.Locale

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
    val (amount, unit) = byteSizeAmount(bytes, ByteSizeUnit.BYTES, ByteSizeUnit.MEGABYTES)
    Timber.d("S2433: file list split %d bytes into %.1f %s", bytes, amount, unit.name)
    return if (unit == ByteSizeUnit.BYTES) {
        FileSizeParts(bytes.toString(), R.string.wear_unit_size_bytes)
    } else {
        val unitRes = if (unit == ByteSizeUnit.MEGABYTES) {
            R.string.wear_unit_size_mb
        } else {
            R.string.wear_unit_size_kb
        }
        FileSizeParts(String.format(locale, "%.1f", amount), unitRes)
    }
}
