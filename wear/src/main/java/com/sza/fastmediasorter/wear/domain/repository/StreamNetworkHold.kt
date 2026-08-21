package com.sza.fastmediasorter.wear.domain.repository

/**
 * Holds a wide-band network for exactly as long as [block] runs.
 *
 * Block-scoped on purpose: the release lives in the implementation's `finally`, so a caller cannot
 * acquire the channel and forget to give it back. A radio held after playback ends is the main cost
 * of this whole approach, and the shape is what keeps it from being paid by accident.
 */
interface StreamNetworkHold {

    suspend fun <T> withWideChannel(block: suspend () -> T): T
}
