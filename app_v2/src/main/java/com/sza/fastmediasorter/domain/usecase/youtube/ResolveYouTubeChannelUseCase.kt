package com.sza.fastmediasorter.domain.usecase.youtube

import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import com.sza.fastmediasorter.domain.repository.YouTubeChannelRepository
import javax.inject.Inject

/** S2032: what the install-time channel question asks - strategic §6.1. */
class ResolveYouTubeChannelUseCase @Inject constructor(
    private val repository: YouTubeChannelRepository,
) {

    suspend operator fun invoke(query: String): YouTubeChannel? = repository.resolveChannel(query)
}
