package com.sza.fastmediasorter.domain.model

enum class ScheduledOpType {
    COPY,
    MOVE,
    DELETE,
    ARCHIVE,

    /** S4248: sends a title, a text and an optional picture to the paired watch; touches no file. */
    WATCH_MESSAGE
}
