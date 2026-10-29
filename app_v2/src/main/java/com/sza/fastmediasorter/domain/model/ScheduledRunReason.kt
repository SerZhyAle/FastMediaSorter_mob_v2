package com.sza.fastmediasorter.domain.model

/**
 * Why a scheduled run was skipped or failed, as a code the screen can turn into a sentence in the
 * interface language. The English text of the same event stays in the run message for the log and the
 * "Details" view; the screen never parses it (S4478).
 *
 * The enum name is what the run journal persists, so a constant is only ever added or retired, never
 * renamed - a retired name reads back as "no reason" instead of failing the row.
 */
enum class ScheduledRunReason {
    SOURCE_UNREACHABLE,
    TARGET_UNREACHABLE,
    SOURCE_READ_ONLY,
    TARGET_READ_ONLY,
    SOURCE_MISSING,
    TARGET_UNAVAILABLE,
    WATCH_UNAVAILABLE,
    WATCH_UNREACHABLE,
    WATCH_REFUSED,
    MESSAGE_TOO_LONG,
    PIN_REFUSED,
    PERMISSION_REQUIRED,

    /** The source resource row is gone, which no later run can repair, so the schedule pauses itself. */
    SOURCE_DELETED,

    /** The target resource row is gone; paused for the same reason as [SOURCE_DELETED]. */
    TARGET_DELETED,
    ;

    companion object {
        /** The constant stored under [name], or null for a missing, empty or retired name. */
        fun fromName(name: String?): ScheduledRunReason? = entries.firstOrNull { it.name == name }
    }
}
