package com.sza.fastmediasorter.domain.model

enum class TimeFilter {
    ALL,
    SINCE_LAST,  // files modified after lastRunAt
    LAST_HOUR,   // files modified in the last 60 minutes
    LAST_DAY     // files modified in the last 24 hours
}
