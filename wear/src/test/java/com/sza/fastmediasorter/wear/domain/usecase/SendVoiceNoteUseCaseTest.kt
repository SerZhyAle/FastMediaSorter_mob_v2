package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendFailureReason
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendResult
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.repository.FakeVoiceNoteRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S1862: the outcome-to-state mapping of one send, and the ADR-3 invariant that no outcome removes
 * the file. Speech cannot be recorded again, so an invariant nobody checks is one that breaks
 * silently - which is the whole reason this test exists rather than a comment.
 */
class SendVoiceNoteUseCaseTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private class FakeFileSender(private val outcome: WearFileSendOutcome) : WearFileSenderRepository {
        var sentFiles: MutableList<File> = mutableListOf()
        override suspend fun sendFile(file: File): com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult {
            sentFiles += file
            return com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult(outcome)
        }
    }

    /** Counted rather than fixed: the ADR-3 case sends once per outcome and would collide on one name. */
    private var noteOrdinal: Int = 0

    private fun noteFile(): File {
        noteOrdinal++
        return temporaryFolder.newFile("audio_260826_1015$noteOrdinal.m4a")
    }

    private fun noteOf(file: File, id: Long = 1L) = VoiceNote(
        id = id,
        fileName = file.name,
        absolutePath = file.absolutePath,
        createdAtMillis = id,
        durationMillis = 1_000L,
        sizeBytes = file.length(),
        deliveryState = VoiceNoteDeliveryState.LOCAL_ONLY
    )

    private fun sendWith(
        outcome: WearFileSendOutcome
    ): Triple<VoiceNoteSendResult, FakeVoiceNoteRepository, File> = runBlocking {
        val file = noteFile()
        val repository = FakeVoiceNoteRepository(listOf(noteOf(file)))
        val result = SendVoiceNoteUseCase(repository, FakeFileSender(outcome))(1L)
        Triple(result, repository, file)
    }

    @Test
    fun `a sent note is recorded as SENT`() {
        val (result, repository, _) = sendWith(WearFileSendOutcome.SENT)
        assertEquals(VoiceNoteSendResult.Sent, result)
        assertEquals(listOf(1L to VoiceNoteDeliveryState.SENT), repository.stateWrites)
    }

    @Test
    fun `an out-of-reach phone leaves the note PENDING, not FAILED`() {
        // The queue is keyed on PENDING: filed as FAILED, the note would never leave on its own and
        // section 11 criterion 4 would be broken with nothing on screen to say so.
        val (result, repository, _) = sendWith(WearFileSendOutcome.PHONE_UNREACHABLE)
        assertEquals(VoiceNoteSendResult.PhoneUnreachable, result)
        assertEquals(listOf(1L to VoiceNoteDeliveryState.PENDING), repository.stateWrites)
    }

    @Test
    fun `a note over the bridge ceiling is FAILED, because waiting cannot shrink it`() {
        val (result, repository, _) = sendWith(WearFileSendOutcome.TOO_LARGE)
        assertEquals(VoiceNoteSendResult.TooLarge, result)
        assertEquals(listOf(1L to VoiceNoteDeliveryState.FAILED), repository.stateWrites)
    }

    @Test
    fun `a transport failure is FAILED and carries its reason`() {
        val (result, repository, _) = sendWith(WearFileSendOutcome.FAILED)
        assertEquals(VoiceNoteSendResult.Failed(VoiceNoteSendFailureReason.TRANSPORT_FAILED), result)
        assertEquals(listOf(1L to VoiceNoteDeliveryState.FAILED), repository.stateWrites)
    }

    @Test
    fun `ADR-3 - the file survives every outcome`() {
        for (outcome in WearFileSendOutcome.entries) {
            val (_, repository, file) = sendWith(outcome)
            assertTrue("$outcome deleted the recording", file.exists())
            assertTrue("$outcome removed the note row", repository.deletedIds.isEmpty())
        }
    }

    @Test
    fun `an id addressing no note reports NOTE_MISSING without opening the bridge`() {
        runBlocking {
            val sender = FakeFileSender(WearFileSendOutcome.SENT)
            val result = SendVoiceNoteUseCase(FakeVoiceNoteRepository(), sender)(404L)
            assertEquals(VoiceNoteSendResult.Failed(VoiceNoteSendFailureReason.NOTE_MISSING), result)
            assertTrue("a missing note still opened a channel", sender.sentFiles.isEmpty())
        }
    }
}
