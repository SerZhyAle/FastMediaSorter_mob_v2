package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S0770: remove a channel from the main-window streams panel by dropping its pin. The channel stays in
 * the catalog (unlike [RemoveStreamSourceUseCase], which deletes the row); the pinned-sources flow then
 * stops emitting it, so the panel updates itself.
 */
class UnpinStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(id: String) = repository.unpin(id)
}
