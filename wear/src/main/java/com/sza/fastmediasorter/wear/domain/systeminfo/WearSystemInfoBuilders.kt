package com.sza.fastmediasorter.wear.domain.systeminfo

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.util.ByteSizeStyle
import com.sza.fastmediasorter.wear.util.ByteSizeUnit
import com.sza.fastmediasorter.wear.util.formatByteSize
import java.util.Locale

/** Below one whole gibibyte the report reads in megabytes, where the number still says something. */
private val REPORT_SIZE_STYLE = ByteSizeStyle(
    minUnit = ByteSizeUnit.MEGABYTES,
    maxUnit = ByteSizeUnit.GIGABYTES,
    decimals = mapOf(ByteSizeUnit.MEGABYTES to 0, ByteSizeUnit.GIGABYTES to 1)
)

/**
 * Formatted with [Locale.US] on purpose: this is a measurement, not prose, and letting the decimal
 * separator follow the watch's locale would make the same reading print differently on two watches.
 */
internal fun formatBytes(bytes: Long): String = formatByteSize(bytes, REPORT_SIZE_STYLE)

/** One line, or nothing at all when the watch would not answer. */
internal fun text(@StringRes labelRes: Int, value: String?): WearSystemInfoField? =
    value?.let { resolved -> WearSystemInfoField(labelRes, WearSystemInfoValue.Text(resolved)) }

/** One line whose value is a fixed word rather than a measurement. */
internal fun label(@StringRes labelRes: Int, @StringRes valueRes: Int): WearSystemInfoField =
    WearSystemInfoField(labelRes, WearSystemInfoValue.Label(valueRes))

/**
 * A section that fell empty says why instead of vanishing (S2165 §5.2). Only a contributor knows which
 * of the two causes applies - hardware this watch does not have, or a reading it refused - so the
 * reason is passed in rather than guessed here.
 */
internal fun section(
    @StringRes titleRes: Int,
    fields: List<WearSystemInfoField>,
    @StringRes emptyReasonRes: Int
): WearSystemInfoSection = if (fields.isEmpty()) {
    WearSystemInfoSection(titleRes, emptyList(), emptyReasonRes)
} else {
    WearSystemInfoSection(titleRes, fields)
}
