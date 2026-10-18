package com.sza.fastmediasorter.domain.model

/**
 * S4282: where one file received from the watch came to rest, and when.
 *
 * [resourceId] is the resource whose root holds the file, which is what Browse opens. It is null when
 * the file landed outside every registered resource (the app's own downloads fallback), because there
 * is then nothing for Browse to open.
 */
data class WatchFileLanding(
    val resourceId: Long?,
    val landedAtEpochMillis: Long
)
