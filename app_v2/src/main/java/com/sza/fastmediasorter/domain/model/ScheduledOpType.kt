package com.sza.fastmediasorter.domain.model

enum class ScheduledOpType {
    COPY,
    MOVE,
    DELETE,
    ARCHIVE,

    /** S4248: sends a title, a text and an optional picture to the paired watch; touches no file. */
    WATCH_MESSAGE,

    /**
     * S4522: fills the source's local volume with temporary files through the storage filler, then deletes
     * them; it reads no files of the source and has no target.
     */
    FILL_FREE_SPACE;

    /** True when the operation writes into a second, target resource. */
    val usesTarget: Boolean
        get() = this == COPY || this == MOVE || this == ARCHIVE

    /** True when the operation lists the source's files, so the file filters apply to it. */
    val usesFileFilters: Boolean
        get() = this != WATCH_MESSAGE && this != FILL_FREE_SPACE
}
