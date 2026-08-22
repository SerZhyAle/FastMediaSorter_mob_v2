package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S1780: removes every channel that arrived by download, keeping the ones the user typed in.
 *
 * "Downloaded" is the stored origin, not a guess: a channel is CATALOG when it came from the curated
 * catalog and IMPORTED when it came from a playlist this app fetched over HTTP. MANUAL is the only origin
 * a person produces by hand, and it is the only one that survives.
 *
 * @return how many channels were removed, so the caller can report the number instead of a bare "done".
 */
class ClearDownloadedStreamsUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(): Int {
        val removed = repository.deleteAllDownloaded()
        Timber.d("S1780: cleared %d downloaded stream(s)", removed)
        return removed
    }
}
