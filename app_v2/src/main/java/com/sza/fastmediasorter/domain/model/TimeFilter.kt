package com.sza.fastmediasorter.domain.model

enum class TimeFilter {
    ALL,

    /** Files modified after `lastRunAt`. */
    SINCE_LAST,

    /** Files modified in the last 60 minutes. */
    LAST_HOUR,

    /** Files modified in the last 24 hours. */
    LAST_DAY
}
