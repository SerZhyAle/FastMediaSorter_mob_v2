package com.sza.fastmediasorter.domain.usecase.youtube

import com.sza.fastmediasorter.domain.model.youtube.YouTubeVideo
import com.sza.fastmediasorter.domain.repository.YouTubeChannelRepository
import javax.inject.Inject

/**
 * S2032: what the placed cell shows while stopped and what its play button starts - strategic §6.3 and
 * §6.4 resolve to one video, so one lookup serves both.
 */
class GetLatestChannelVideoUseCase @Inject constructor(
    private val repository: YouTubeChannelRepository,
) {

    suspend operator fun invoke(channelId: String): YouTubeVideo? = repository.latestVideo(channelId)
}
