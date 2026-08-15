package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamQualityMemoryEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.data.util.StreamUrlNormalizer
import javax.inject.Inject

/**
 * S1511: what a previous session learned about this channel's video-quality rung, or null when it has
 * never settled anywhere.
 *
 * A one-shot read rather than an observed flow: the single consumer is the stream session, which reads
 * once at the moment the rendition ladder becomes known and never again.
 *
 * The address is normalized here rather than by the caller, so the read and the write of the same channel
 * cannot disagree about its key.
 */
class GetStreamQualityMemoryUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(url: String): StreamQualityMemoryEntity? =
        repository.learnedRung(StreamUrlNormalizer.normalize(url))
}
