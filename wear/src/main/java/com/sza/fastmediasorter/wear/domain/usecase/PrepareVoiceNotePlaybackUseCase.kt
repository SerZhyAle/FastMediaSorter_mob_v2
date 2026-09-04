package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_VOICE_NOTE
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import timber.log.Timber
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
    private val prepareWearFilePlayback: PrepareWearFilePlaybackUseCase,
    private val selectedMediaManager: SelectedMediaManager
) {

    operator fun invoke(note: VoiceNote): Long? {
        Timber.d("S2161: voice-note playback requested")
        return publishedTargetId(note) ?: privateFileId(note)
    }

    /**
     * Keep the published entry selected while the player opens it, so a deleted MediaStore row can
     * be identified as an unavailable recording instead of looking like a generic missing file.
     */
    private fun publishedTargetId(note: VoiceNote): Long? =
        note.publishedAddress
            ?.let(Uri::parse)
            ?.let { uri -> uri.lastPathSegment?.toLongOrNull()?.also { selectPublishedNote(note, uri, it) } }

    private fun selectPublishedNote(note: VoiceNote, uri: Uri, id: Long) {
        selectedMediaManager.selectFile(
            file = WearMediaFile(
                id = id,
                name = note.fileName,
                uri = uri,
                mimeType = PRIVATE_NOTE_MIME_TYPE,
                size = note.sizeBytes,
                dateModified = note.createdAtMillis,
                duration = note.durationMillis
            ),
            isNetworkSource = false,
            streamUri = note.publishedAddress,
            sourceId = SOURCE_ID_VOICE_NOTE,
            isDirectStream = false
        )
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
