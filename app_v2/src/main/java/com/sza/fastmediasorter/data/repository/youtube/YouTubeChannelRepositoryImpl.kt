package com.sza.fastmediasorter.data.repository.youtube

import com.sza.fastmediasorter.data.youtube.YouTubeFeedChannelProvider
import com.sza.fastmediasorter.domain.model.youtube.YouTubeChannel
import com.sza.fastmediasorter.domain.model.youtube.YouTubeVideo
import com.sza.fastmediasorter.domain.repository.YouTubeChannelRepository
import javax.inject.Inject
import javax.inject.Singleton

/** S2032: the seam between the launcher's cell and the keyless provider - strategic §5.3. */
@Singleton
class YouTubeChannelRepositoryImpl @Inject constructor(
    private val provider: YouTubeFeedChannelProvider,
) : YouTubeChannelRepository {

    override suspend fun resolveChannel(query: String): YouTubeChannel? = provider.resolveChannel(query)

    override suspend fun latestVideo(channelId: String): YouTubeVideo? = provider.latestVideo(channelId)
}
