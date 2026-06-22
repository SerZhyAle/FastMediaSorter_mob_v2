package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S0581: resolve the saved stream behind a playback URL. Returns null when the URL is not a list
 * entry, which lets the player fall back to its generic error handling instead of offering removal.
 */
class GetStreamSourceByUrlUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(url: String): StreamSourceEntity? = repository.getByUrl(url)
}
