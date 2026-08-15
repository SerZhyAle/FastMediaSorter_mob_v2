package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.data.util.StreamUrlNormalizer
import javax.inject.Inject

/**
 * S1511: persist the rung this channel settled on, then keep the store from growing without end.
 *
 * The write prunes on every call rather than on a schedule: there is no other moment this table is
 * touched, so a separate sweep would need a trigger of its own for a table that only ever grows by one
 * row at a time.
 *
 * Retention and the channel cap come from the measurement the feature was designed against (strategic
 * section 0, item 3) - a verdict older than a week describes a network the channel may no longer be on.
 */
class RecordStreamQualityMemoryUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(
        url: String,
        rungBitrateBps: Int,
        ceilingWidthPx: Int,
        ceilingHeightPx: Int,
        probeFailures: Int,
        atMillis: Long
    ) {
        repository.rememberRung(
            normalizedUrl = StreamUrlNormalizer.normalize(url),
            bitrateBps = rungBitrateBps,
            widthPx = ceilingWidthPx,
            heightPx = ceilingHeightPx,
            failures = probeFailures,
            atMillis = atMillis
        )
        repository.pruneQualityMemory(
            olderThanMillis = atMillis - RETENTION_MS,
            keepNewestChannels = MAX_REMEMBERED_CHANNELS
        )
    }

    private companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_REMEMBERED_CHANNELS = 200
    }
}
