package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearFilePlaybackTarget
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearNetworkFileOpenRequest
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import timber.log.Timber
import javax.inject.Inject

/**
 * S2694: turns a file the network folder walk found into what a player needs to start.
 *
 * The walk had no way to open a file at all before this: its screen handed every tapped row to the
 * host's local resolution, which knows a `file://` uri and a MediaStore id and nothing else, so a
 * network row resolved to "nothing selected" and the player opened empty. Nothing crashed and no
 * gate saw it, because the directory half of the walk was unaffected.
 *
 * It publishes through [SelectedMediaManager] rather than addressing the share itself, which is the
 * one mechanism every playback origin on this watch already shares - the flat network listing
 * included. A second hand-off would be free to disagree with that one about the same file on the
 * same share, which is the drift strategic ADR-1 rules out for the walk as a whole.
 *
 * No playback set is published. The walk pages a level rather than holding a finished list, so there
 * is no set to step through, and this matches what [PrepareWearFilePlaybackUseCase] leaves behind.
 */
class PrepareWearNetworkFilePlaybackUseCase @Inject constructor(
    private val selectedMediaManager: SelectedMediaManager
) {

    /**
     * The id is the address's hash rather than a listing index.
     *
     * The flat listing can use an index because it holds the whole listing it minted them from; the
     * walk holds one window of one level and re-reads it on every ascent, so an index would name a
     * different file after the wearer stepped up and back down.
     */
    operator fun invoke(request: WearNetworkFileOpenRequest): WearFilePlaybackTarget {
        val address = request.uri.toString()
        Timber.d("S2694: walk file handed to a player: %s of source %s", address, request.sourceId)
        val mediaFile = WearMediaFile(
            id = address.hashCode().toLong(),
            name = request.name,
            uri = request.uri,
            mimeType = request.mimeType,
            size = request.sizeBytes,
            dateModified = request.dateModifiedEpochSeconds
        )
        selectedMediaManager.selectFile(
            file = mediaFile,
            isNetworkSource = true,
            streamUri = address,
            sourceId = request.sourceId
        )
        return WearFilePlaybackTarget(fileId = mediaFile.id, mimeType = request.mimeType.orEmpty())
    }
}
