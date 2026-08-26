package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendFailureReason
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendResult
import com.sza.fastmediasorter.wear.domain.model.toVoiceNoteSendResult
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S1862: hands one note to the phone over the S1861 bridge and records what came back.
 *
 * ADR-1 keeps this on that one transport: a second protocol in the same direction would diverge on
 * ceilings, states and errors, and the two would have to be debugged separately. ADR-3 keeps the
 * file: speech cannot be recorded again, so no outcome here removes anything from disk - a note that
 * failed to send is still a note. Retrying is not done here either (section 5.1 item 4 asks for a
 * legible outcome without a hidden retry); DrainPendingVoiceNotesUseCase owns the second attempt.
 */
class SendVoiceNoteUseCase @Inject constructor(
    private val noteRepository: VoiceNoteRepository,
    private val fileSenderRepository: WearFileSenderRepository
) {

    suspend operator fun invoke(noteId: Long): VoiceNoteSendResult {
        val note = noteRepository.noteById(noteId)
        if (note == null) {
            Timber.w("No voice note with id %d to send", noteId)
            return VoiceNoteSendResult.Failed(VoiceNoteSendFailureReason.NOTE_MISSING)
        }
        val result = fileSenderRepository.sendFile(File(note.absolutePath)).toVoiceNoteSendResult()
        noteRepository.updateState(noteId, result.toDeliveryState())
        return result
    }
}

/**
 * PENDING only for an out-of-reach phone: that is the one outcome the queue can resolve by waiting,
 * and every other unhappy end stays FAILED so the list does not promise a delivery that will not
 * happen on its own.
 */
private fun VoiceNoteSendResult.toDeliveryState(): VoiceNoteDeliveryState = when (this) {
    is VoiceNoteSendResult.Sent -> VoiceNoteDeliveryState.SENT
    is VoiceNoteSendResult.PhoneUnreachable -> VoiceNoteDeliveryState.PENDING
    is VoiceNoteSendResult.TooLarge -> VoiceNoteDeliveryState.FAILED
    is VoiceNoteSendResult.Failed -> VoiceNoteDeliveryState.FAILED
}
