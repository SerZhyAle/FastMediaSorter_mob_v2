package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearFilePlaybackTarget
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import java.io.File
import javax.inject.Inject

/**
 * S1884: turns a file the phone just delivered into what a player needs to start.
 *
 * The players address a file by id and read it through [SelectedMediaManager] - the same hand-off the
 * phone-resource screen already performs for a delivered copy, because a file that arrived over the
 * bridge has no MediaStore row for a player to look up. Written as a use case rather than inline in
 * the host for the same reason [PrepareWearStreamPlaybackUseCase] was extracted: the request arrives
 * at the Data Layer listener, and a second copy of this preparation would be free to drift from the
 * first in a way only a real watch could reveal.
 *
 * No playback set is published. The phone sent one file to be looked at, so there is nothing to page
 * through, and a set of one is what the shipped phone-resource path also leaves behind.
 */
class PrepareWearFilePlaybackUseCase @Inject constructor(
    private val selectedMediaManager: SelectedMediaManager,
) {

    operator fun invoke(request: WearFileOpenRequest): WearFilePlaybackTarget {
        val file = File(request.path)
        val mediaFile = WearMediaFile(
            id = request.path.hashCode().toLong(),
            name = file.name,
            uri = Uri.fromFile(file),
            mimeType = request.mimeType,
            size = file.length(),
            dateModified = 0L,
        )
        selectedMediaManager.selectFile(file = mediaFile, isNetworkSource = false)
        return WearFilePlaybackTarget(fileId = mediaFile.id, mimeType = request.mimeType)
    }
}
