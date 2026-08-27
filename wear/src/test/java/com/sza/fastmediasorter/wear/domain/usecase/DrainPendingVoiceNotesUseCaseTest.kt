package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.repository.FakeVoiceNoteRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * S1862: the queue that empties itself when the link returns.
 *
 * Section 11 criterion 4 requires a note taken out of reach to leave on its own, and section 7 calls
 * the queue growing unnoticed its own risk - so both the stop rule and the no-double-send rule are
 * asserted here. A double send would put duplicates in the phone's destination, which the user sees
 * and cannot undo.
 */
class DrainPendingVoiceNotesUseCaseTest {

    /**
     * Answers a scripted outcome per call and suspends while doing it, so two concurrent drains
     * genuinely overlap - an instant fake would serialise by accident and prove nothing about the lock.
     */
    private class ScriptedFileSender(
        private val outcomes: List<WearFileSendOutcome>,
        private val fallback: WearFileSendOutcome = WearFileSendOutcome.SENT
    ) : WearFileSenderRepository {

        val sentFiles: MutableList<File> = mutableListOf()

        override suspend fun sendFile(file: File): com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult {
            val index = sentFiles.size
            sentFiles += file
            delay(SEND_DELAY_MILLIS)
            return com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult(outcomes.getOrElse(index) { fallback })
        }

        private companion object {
            const val SEND_DELAY_MILLIS = 50L
        }
    }

    private fun pendingNotes(count: Int): List<VoiceNote> = (1..count).map { index ->
        VoiceNote(
            id = index.toLong(),
            fileName = "audio_$index.m4a",
            absolutePath = "/watch/voice_notes/audio_$index.m4a",
            createdAtMillis = index.toLong(),
            durationMillis = 1_000L,
            sizeBytes = 2_048L,
            deliveryState = VoiceNoteDeliveryState.PENDING
        )
    }

    private fun useCase(
        repository: FakeVoiceNoteRepository,
        sender: ScriptedFileSender
    ) = DrainPendingVoiceNotesUseCase(repository, SendVoiceNoteUseCase(repository, sender))

    @Test
    fun `every pending note is sent in turn`() = runTest {
        val repository = FakeVoiceNoteRepository(pendingNotes(THREE_NOTES))
        val sender = ScriptedFileSender(emptyList())
        useCase(repository, sender)()
        assertEquals(THREE_NOTES, sender.sentFiles.size)
        assertEquals(emptyList<VoiceNote>(), repository.pendingNow())
    }

    @Test
    fun `the run stops at the first out-of-reach phone`() = runTest {
        // One link serves the whole queue, so the notes after it would each pay a connect timeout to
        // learn what this attempt already established.
        val repository = FakeVoiceNoteRepository(pendingNotes(THREE_NOTES))
        val sender = ScriptedFileSender(
            listOf(WearFileSendOutcome.SENT, WearFileSendOutcome.PHONE_UNREACHABLE)
        )
        useCase(repository, sender)()
        assertEquals(2, sender.sentFiles.size)
        // Both survivors stay pending, and for different reasons: the second note met the closed link,
        // the third was never tried at all. Either way the next run picks them up.
        assertEquals(2, repository.pendingNow().size)
    }

    @Test
    fun `a concurrent second drain does not send a note twice`() = runTest {
        // The Mutex is the entire guarantee: the listener callback and application start can both fire
        // the drain within the same second, and a duplicate lands in the phone's destination for good.
        val repository = FakeVoiceNoteRepository(pendingNotes(THREE_NOTES))
        val sender = ScriptedFileSender(emptyList())
        val drain = useCase(repository, sender)
        val first = launch { drain() }
        val second = launch { drain() }
        first.join()
        second.join()
        assertEquals(THREE_NOTES, sender.sentFiles.size)
    }

    private companion object {
        const val THREE_NOTES = 3
    }
}
