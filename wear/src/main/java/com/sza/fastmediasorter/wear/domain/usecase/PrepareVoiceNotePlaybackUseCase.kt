package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import java.io.File
import javax.inject.Inject

/**
 * S2161: prepares a [VoiceNote] for playback in the watch audio player.
 *
 * A published note resolves to its MediaStore row id. A private note (pre-Q or failed publish)
 * routes through [PrepareWearFilePlaybackUseCase] so it plays from private storage without a MediaStore row.
 * Returns null if neither address can be resolved.
 */
class PrepareVoiceNotePlaybackUseCase @Inject constructor(
    private val prepareWearFilePlayback: PrepareWearFilePlaybackUseCase
) {

    operator fun invoke(note: VoiceNote): Long? {
        val publishedId = note.publishedAddress
            ?.let { Uri.parse(it).lastPathSegment?.toLongOrNull() }
        return publishedId ?: privateFileId(note)
    }

    /** A note that never published, or whose publish failed, still plays from the private copy (ADR-3). */
    private fun privateFileId(note: VoiceNote): Long? {
        val privateFile = File(note.absolutePath)
        if (!privateFile.exists() || privateFile.length() == 0L) return null
        return prepareWearFilePlayback(
            WearFileOpenRequest(
                path = note.absolutePath,
                mimeType = PRIVATE_NOTE_MIME_TYPE
            )
        ).fileId
    }

    private companion object {
        const val PRIVATE_NOTE_MIME_TYPE = "audio/mp4"
    }
}
