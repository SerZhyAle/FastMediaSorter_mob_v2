package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S2161: unit tests for [PrepareVoiceNotePlaybackUseCase].
 */
class PrepareVoiceNotePlaybackUseCaseTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val selectedMediaManager: SelectedMediaManager = mockk(relaxed = true)
    private lateinit var prepareFilePlayback: PrepareWearFilePlaybackUseCase
    private lateinit var useCase: PrepareVoiceNotePlaybackUseCase

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val str = firstArg<String>()
            // Stubbed through the mock's own reference: inside a mockk { .. } builder block the
            // receiver of every { .. } is the matcher scope, so a receiverless call there stubs
            // nothing and mockk reports a missing mocked call.
            val uri: Uri = mockk()
            every { uri.lastPathSegment } returns str.substringAfterLast('/')
            uri
        }
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)
        prepareFilePlayback = PrepareWearFilePlaybackUseCase(selectedMediaManager)
        useCase = PrepareVoiceNotePlaybackUseCase(prepareFilePlayback)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `published note resolves to its media store row id`() {
        val note = VoiceNote(
            id = 1L,
            fileName = "audio_1.m4a",
            absolutePath = "/non/existent/path",
            createdAtMillis = 1000L,
            durationMillis = 5000L,
            sizeBytes = 1024L,
            deliveryState = VoiceNoteDeliveryState.SENT,
            publishedAddress = "content://media/external/audio/media/9876"
        )

        val targetId = useCase(note)

        assertEquals(9876L, targetId)
    }

    @Test
    fun `private note with existing file resolves via PrepareWearFilePlaybackUseCase`() {
        val file = temporaryFolder.newFile("audio_2.m4a").apply { writeText("audio") }
        val note = VoiceNote(
            id = 2L,
            fileName = file.name,
            absolutePath = file.absolutePath,
            createdAtMillis = 2000L,
            durationMillis = 3000L,
            sizeBytes = file.length(),
            deliveryState = VoiceNoteDeliveryState.LOCAL_ONLY,
            publishedAddress = null
        )

        val targetId = useCase(note)

        assertEquals(file.absolutePath.hashCode().toLong(), targetId)
    }

    @Test
    fun `note with neither valid published id nor existing file returns null`() {
        val note = VoiceNote(
            id = 3L,
            fileName = "audio_missing.m4a",
            absolutePath = "/invalid/path/does_not_exist.m4a",
            createdAtMillis = 3000L,
            durationMillis = 3000L,
            sizeBytes = 100L,
            deliveryState = VoiceNoteDeliveryState.LOCAL_ONLY,
            publishedAddress = null
        )

        val targetId = useCase(note)

        assertNull(targetId)
    }
}
