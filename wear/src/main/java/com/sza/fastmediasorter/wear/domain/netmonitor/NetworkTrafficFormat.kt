package com.sza.fastmediasorter.wear.domain.netmonitor

import com.sza.fastmediasorter.wear.util.ByteSizeStyle
import com.sza.fastmediasorter.wear.util.ByteSizeUnit
import com.sza.fastmediasorter.wear.util.formatByteSize

/** A cumulative counter climbs into gigabytes, so it gets the whole ladder and two decimals at the top. */
private val TRAFFIC_TOTAL_STYLE = ByteSizeStyle(
    minUnit = ByteSizeUnit.BYTES,
    maxUnit = ByteSizeUnit.GIGABYTES,
    decimals = mapOf(
        ByteSizeUnit.BYTES to 0,
        ByteSizeUnit.KILOBYTES to 0,
        ByteSizeUnit.MEGABYTES to 1,
        ByteSizeUnit.GIGABYTES to 2
    )
)

/** A per-second rate above a megabyte is already off the scale of a watch link, so the ladder stops there. */
private val TRAFFIC_RATE_STYLE = ByteSizeStyle(
    minUnit = ByteSizeUnit.BYTES,
    maxUnit = ByteSizeUnit.MEGABYTES,
    decimals = mapOf(
        ByteSizeUnit.BYTES to 0,
        ByteSizeUnit.KILOBYTES to 1,
        ByteSizeUnit.MEGABYTES to 2
    )
)

/** Throughput as `<size>/s`. */
internal fun formatRate(bytesPerSec: Long): String = formatByteSize(bytesPerSec, TRAFFIC_RATE_STYLE) + "/s"

/**
 * A cumulative received or transmitted total.
 *
 * Named apart from the system-info report's `formatBytes` on purpose: the two print the same
 * quantity on different ladders, and one name over both invited a caller to reach for whichever the
 * import happened to resolve.
 */
internal fun formatTrafficTotal(bytes: Long): String = formatByteSize(bytes, TRAFFIC_TOTAL_STYLE)
