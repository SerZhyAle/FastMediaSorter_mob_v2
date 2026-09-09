package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamPlaybackTarget
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import javax.inject.Inject

/**
 * S2551: opens a stream that exists only for as long as the session serving it does.
 *
 * A sibling of [PrepareWearStreamPlaybackUseCase] rather than a flag on it. The phone's camera
 * address is a port that will not exist tomorrow, and the shared use case writes two records that
 * outlive the session - the play count and the home screen's recent row, which the launch-target
 * resolver reopens. Strategic §5.2 requires this path to miss both.
 *
 * The mechanism is what the constructor does NOT take: neither `WearStreamUsageRepository` nor
 * `WearPreferencesRepository` is injected here, so the two writes are unreachable rather than
 * conditionally skipped. That is deliberate and not an oversight - a `skipHistory` flag on the
 * shared use case would leave the next writer inside it free to forget.
 *
 * Not suspending, for the same reason: the suspension of the shared use case exists to write the
 * home row, and nothing here writes anything durable.
 */
class PrepareEphemeralStreamPlaybackUseCase @Inject constructor(
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager,
) {

    /**
     * Selects [url] for playback and publishes a set of exactly one.
     *
     * A set of one rather than no set at all: the players page through the published set, and an
     * absent set reads to them as "nothing is open" rather than as "there is nowhere to page to".
     */
    operator fun invoke(
        url: String,
        title: String,
        isVideo: Boolean = true,
    ): WearStreamPlaybackTarget {
        val mediaFile = ephemeralChannel(url, title, isVideo).toWearMediaFile(isVideo)

        selectedMediaManager.selectFile(
            file = mediaFile,
            isNetworkSource = true,
            streamUri = url,
            sourceId = SOURCE_ID_STREAM,
            isDirectStream = true,
        )
        playbackSetManager.publish(listOf(mediaFile), START_INDEX)

        return WearStreamPlaybackTarget(fileId = mediaFile.id, isVideo = isVideo)
    }

    /**
     * A channel value built to be mapped and thrown away - it is never stored, exported or pinned.
     *
     * It exists so the shared mapper answers for this path too; inlining the mapping here would be
     * the second copy [PrepareWearStreamPlaybackUseCase] forbids.
     */
    private fun ephemeralChannel(url: String, title: String, isVideo: Boolean) = WearStreamChannel(
        id = url,
        name = title,
        url = url,
        mediaKind = if (isVideo) {
            ClassifyWearStreamMediaKindUseCase.VIDEO
        } else {
            ClassifyWearStreamMediaKindUseCase.AUDIO
        },
    )

    private companion object {
        const val START_INDEX = 0
    }
}
