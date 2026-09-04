package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamPlaybackTarget
import com.sza.fastmediasorter.wear.domain.model.foldWearStreamIdentity
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamUsageRepository
import javax.inject.Inject

/**
 * S1944: turns a channel into "what the player needs to start", so that both ways of reaching a
 * player agree by construction.
 *
 * Extracted from the streams list's ViewModel (S1708/S1871) because it stopped being the only
 * entrance: the phone can now ask the watch to open a channel, and that request arrives at the Data
 * Layer listener, nowhere near the list's ViewModel. A second copy of this preparation would be a
 * second answer to "which player, and what is next in the set" - the two would drift, and the drift
 * would only ever be visible on a watch.
 */
class PrepareWearStreamPlaybackUseCase @Inject constructor(
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
    private val usageRepository: WearStreamUsageRepository,
    private val preferencesRepository: WearPreferencesRepository,
) {

    /**
     * Selects [channel] for playback and publishes the set to page through.
     *
     * [siblings] is the list the user was looking at, so paging stays inside it; the phone-initiated
     * path passes nothing and gets a set of one, which is exactly what it asked for.
     *
     * S2499: suspending because the home row's record is written here. Three of the four callers were
     * suspending already; the fourth wraps the tap in its screen's scope. A coroutine scope owned by
     * this use case was the alternative and was rejected - the use-case layer does not own lifetimes.
     */
    suspend operator fun invoke(
        channel: WearStreamChannel,
        siblings: List<WearStreamChannel> = emptyList(),
    ): WearStreamPlaybackTarget {
        val isVideo = channel.isVideoKind()
        val mediaFile = channel.toMediaFile(isVideo)

        // S2146: counted here rather than in the list's ViewModel, because this is the one point the
        // list entrance and the phone's Data Layer request already share - counting at either
        // entrance alone would make a channel opened from the phone invisible to the default order.
        // Folded, not merely normalized: the projection looks the count up by the same folded identity
        // it partitions pins by, and a station reached over http on one day and https on the next is
        // one station to the owner. Writing the un-folded form here would key a web channel's count to
        // an address the reader never asks for, so every play of it would count into nothing.
        usageRepository.recordPlay(foldWearStreamIdentity(channel.url))

        // S2499: the home screen's recent row is fed from here for the same reason the play count is -
        // this is the one point the watch's channel list and the phone's open-channel request share,
        // so a channel opened from the phone reaches the row without a second writer.
        // Normalized rather than folded, unlike the count above: the shortcut is reopened by the
        // launch-target resolver, which finds the channel by the normalized spelling. A folded key
        // would need a second catalog lookup, which is a second answer to which channel this is.
        preferencesRepository.setLastUsedStream(normalizeWearStreamUrl(channel.url), channel.name)

        selectedMediaManager.selectFile(
            file = mediaFile,
            isNetworkSource = true,
            streamUri = channel.url,
            // S2039: the domain constant, not a private copy - the two agreed by value but not by
            // definition, so a change to one would not have reached the other.
            sourceId = SOURCE_ID_STREAM,
            isDirectStream = true,
        )

        // Same kind only: paging from a radio station into a video feed would swap the player under
        // the user mid-gesture.
        val set = siblings.filter { it.isVideoKind() == isVideo }.ifEmpty { listOf(channel) }
        val startIndex = set.indexOfFirst { it.url == channel.url }.coerceAtLeast(0)
        playbackSetManager.publish(set.map { it.toMediaFile(isVideo) }, startIndex)

        return WearStreamPlaybackTarget(fileId = mediaFile.id, isVideo = isVideo)
    }

    private fun WearStreamChannel.toMediaFile(isVideo: Boolean) = WearMediaFile(
        id = url.hashCode().toLong(),
        name = name,
        uri = Uri.parse(url),
        mimeType = if (isVideo) MIME_VIDEO else MIME_AUDIO,
        size = 0L,
        dateModified = 0L,
    )

    private companion object {
        const val MIME_VIDEO = "video/*"
        const val MIME_AUDIO = "audio/*"
    }
}

/**
 * A catalog row carries its kind as free text, so VIDEO and RTSP both mean "the video player". Kept
 * next to the only code that acts on it, so the two spellings can never drift apart between callers.
 */
internal fun WearStreamChannel.isVideoKind(): Boolean =
    mediaKind.equals(ClassifyWearStreamMediaKindUseCase.VIDEO, ignoreCase = true) ||
        mediaKind.equals(ClassifyWearStreamMediaKindUseCase.RTSP, ignoreCase = true)
