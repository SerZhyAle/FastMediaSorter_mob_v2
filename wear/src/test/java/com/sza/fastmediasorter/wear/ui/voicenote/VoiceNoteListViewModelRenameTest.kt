package com.sza.fastmediasorter.wear.ui.voicenote

import android.net.Uri
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S2495: the rename that has to move both halves of a note or neither.
 *
 * Strategic §7 rates a split name the ticket's highest risk - one recording under two names, with the
 * index having lost its file - so both failure directions get their own assertion, and each asserts
 * what happened to the OTHER half rather than only the returned verdict.
 */
class VoiceNoteListViewModelRenameTest {

    /** Every published-half call in order, so a rollback is visible as a second call and not inferred. */
    private val publishedCalls = mutableListOf<String>()

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)
        every { Uri.parse(any()) } returns mockk<Uri>(relaxed = true)
        mockkStatic(MimeTypeMap::class)
        val mimeTypeMap: MimeTypeMap = mockk()
        every { MimeTypeMap.getSingleton() } returns mimeTypeMap
        every { mimeTypeMap.getMimeTypeFromExtension(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(MimeTypeMap::class)
    }

    /**
     * The published half is asked first because it is the one that can refuse. A refusal there must
     * leave the private file and the index untouched - the private half is never even called.
     */
    @Test
    fun `a refused published rename never reaches the private half`() = runTest {
        var privateCalls = 0

        val outcome = renameVoiceNote(
            note = note(PUBLISHED_ADDRESS),
            newName = NEW_NAME,
            renamePublished = { _, name ->
                publishedCalls += name
                false
            },
            renamePrivate = {
                privateCalls++
                true
            }
        )

        assertEquals(VoiceNoteRenameOutcome.REFUSED_BEFORE_ANY_MOVE, outcome)
        assertEquals(0, privateCalls)
        assertEquals(listOf(NEW_NAME), publishedCalls)
    }

    /**
     * The mirror case: the published half agreed and the private half then failed, so the row is put
     * back under its OLD name - which is what the second recorded call has to be.
     */
    @Test
    fun `a failed private rename puts the published row back under its old name`() = runTest {
        val outcome = renameVoiceNote(
            note = note(PUBLISHED_ADDRESS),
            newName = NEW_NAME,
            renamePublished = { _, name ->
                publishedCalls += name
                true
            },
            renamePrivate = { false }
        )

        assertEquals(VoiceNoteRenameOutcome.ROLLED_BACK, outcome)
        assertEquals(listOf(NEW_NAME, FILE_NAME), publishedCalls)
    }

    /** An unpublished note has one half only, so nothing is asked of the shared collection at all. */
    @Test
    fun `an unpublished note renames without touching the shared collection`() = runTest {
        val outcome = renameVoiceNote(
            note = note(publishedAddress = null),
            newName = NEW_NAME,
            renamePublished = { _, name ->
                publishedCalls += name
                true
            },
            renamePrivate = { true }
        )

        assertEquals(VoiceNoteRenameOutcome.SUCCEEDED, outcome)
        assertEquals(emptyList<String>(), publishedCalls)
    }

    /** An unpublished note whose file will not move fails without a row to put back. */
    @Test
    fun `an unpublished note that cannot move reports a rollback with nothing to roll back`() = runTest {
        val outcome = renameVoiceNote(
            note = note(publishedAddress = null),
            newName = NEW_NAME,
            renamePublished = { _, name ->
                publishedCalls += name
                true
            },
            renamePrivate = { false }
        )

        assertEquals(VoiceNoteRenameOutcome.ROLLED_BACK, outcome)
        assertEquals(emptyList<String>(), publishedCalls)
    }

    @Test
    fun `both halves moving is the success case`() = runTest {
        val outcome = renameVoiceNote(
            note = note(PUBLISHED_ADDRESS),
            newName = NEW_NAME,
            renamePublished = { _, name ->
                publishedCalls += name
                true
            },
            renamePrivate = { true }
        )

        assertEquals(VoiceNoteRenameOutcome.SUCCEEDED, outcome)
        assertEquals(listOf(NEW_NAME), publishedCalls)
    }

    private fun note(publishedAddress: String?) = VoiceNote(
        id = NOTE_ID,
        fileName = FILE_NAME,
        absolutePath = "/data/voice_notes/$FILE_NAME",
        createdAtMillis = CREATED_AT_MILLIS,
        durationMillis = DURATION_MILLIS,
        sizeBytes = SIZE_BYTES,
        deliveryState = VoiceNoteDeliveryState.LOCAL_ONLY,
        publishedAddress = publishedAddress
    )

    private companion object {
        const val NOTE_ID = 11L
        const val FILE_NAME = "audio_260903_155943.m4a"
        const val NEW_NAME = "shopping list.m4a"
        const val PUBLISHED_ADDRESS = "content://media/external/audio/media/42"
        const val CREATED_AT_MILLIS = 1_756_900_000_000L
        const val DURATION_MILLIS = 4_200L
        const val SIZE_BYTES = 65_536L
    }
}
