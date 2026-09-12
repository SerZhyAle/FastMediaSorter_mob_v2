package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendResult
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1862: sends the notes that were taken while the phone was out of reach.
 *
 * Section 11 criterion 4 requires such a note to leave on its own once the link is back, so this is
 * called from the link returning and from application start rather than from a screen - a note the
 * user has forgotten about must still go.
 *
 * Singleton on purpose: the [Mutex] is the whole re-entrancy guarantee, and a per-injection instance
 * would give each caller its own lock and let two drains send the same note twice.
 */
@Singleton
class DrainPendingVoiceNotesUseCase @Inject constructor(
    private val noteRepository: VoiceNoteRepository,
    private val sendVoiceNoteUseCase: SendVoiceNoteUseCase
) {

    private val drainMutex = Mutex()

    suspend operator fun invoke() {
        drainMutex.withLock {
            for (note in noteRepository.pendingNow()) {
                val result = sendVoiceNoteUseCase(note.id)
                if (result is VoiceNoteSendResult.PhoneUnreachable) {
                    // One link serves every note, so the rest would each pay a full connect timeout
                    // to learn what this one already established. They stay pending for the next run.
                    Timber.i("Stopping the voice-note drain: the phone is out of reach")
                    break
                }
            }
        }
    }
}
