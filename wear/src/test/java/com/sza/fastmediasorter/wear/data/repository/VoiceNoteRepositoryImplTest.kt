package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.data.db.VoiceNoteDao
import com.sza.fastmediasorter.wear.data.db.VoiceNoteEntity
import com.sza.fastmediasorter.wear.data.recorder.VoiceNoteFileFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S1862: the two store rules the user can feel - a deletion that actually frees the watch, and a
 * refusal to start a recording that cannot fit.
 *
 * Both are section 7 mitigations of the same high-probability risk (a full watch), and a deletion
 * that drops only the row would satisfy the list while leaving the bytes exactly where they were.
 */
class VoiceNoteRepositoryImplTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private class FakeVoiceNoteDao(initial: List<VoiceNoteEntity> = emptyList()) : VoiceNoteDao {

        val rows: MutableMap<Long, VoiceNoteEntity> = initial.associateBy { it.id }.toMutableMap()

        override fun observeAll(): Flow<List<VoiceNoteEntity>> = MutableStateFlow(rows.values.toList())

        override fun observePending(pendingState: String): Flow<List<VoiceNoteEntity>> =
            MutableStateFlow(rows.values.filter { it.deliveryState == pendingState })

        override suspend fun getPendingOnce(pendingState: String): List<VoiceNoteEntity> =
            rows.values.filter { it.deliveryState == pendingState }

        override suspend fun insert(note: VoiceNoteEntity): Long {
            val id = (rows.keys.maxOrNull() ?: 0L) + 1L
            rows[id] = note.copy(id = id)
            return id
        }

        override suspend fun updateDeliveryState(id: Long, state: String) {
            rows[id]?.let { rows[id] = it.copy(deliveryState = state) }
        }

        override suspend fun updatePublishedAddress(id: Long, address: String) {
            rows[id]?.let { rows[id] = it.copy(publishedAddress = address) }
        }

        override suspend fun updateName(id: Long, fileName: String, absolutePath: String) {
            rows[id]?.let { rows[id] = it.copy(fileName = fileName, absolutePath = absolutePath) }
        }

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }

        override suspend fun getById(id: Long): VoiceNoteEntity? = rows[id]
    }

    private fun factoryWithFreeSpace(freeBytes: Long): VoiceNoteFileFactory {
        // The directory is mocked rather than real because a real one reports the machine's own free
        // space, which no test can put below the threshold without filling the disk.
        val directory = mockk<File>()
        every { directory.usableSpace } returns freeBytes
        val factory = mockk<VoiceNoteFileFactory>()
        every { factory.directory() } returns directory
        return factory
    }

    private fun entityFor(file: File, id: Long = 1L) = VoiceNoteEntity(
        id = id,
        fileName = file.name,
        absolutePath = file.absolutePath,
        createdAtMillis = id,
        durationMillis = 1_000L,
        sizeBytes = file.length(),
        deliveryState = "LOCAL_ONLY"
    )

    @Test
    fun `deleting a note removes the row and the file`() = runBlocking {
        val file = temporaryFolder.newFile("audio_260826_101500.m4a")
        val dao = FakeVoiceNoteDao(listOf(entityFor(file)))
        val repository = VoiceNoteRepositoryImpl(context, dao, factoryWithFreeSpace(FREE_ABOVE_THRESHOLD))

        repository.delete(1L)

        assertFalse("the recording still occupies the watch", file.exists())
        assertNull("the row outlived its file", dao.getById(1L))
    }

    @Test
    fun `deleting a note with published address deletes published entry via content resolver`() = runBlocking {
        val file = temporaryFolder.newFile("audio_260826_101502.m4a")
        val entity = entityFor(file).copy(publishedAddress = "content://media/external/audio/media/789")
        val dao = FakeVoiceNoteDao(listOf(entity))
        val repository = VoiceNoteRepositoryImpl(context, dao, factoryWithFreeSpace(FREE_ABOVE_THRESHOLD))

        repository.delete(1L)

        assertFalse("the working copy still occupies the watch", file.exists())
        assertNull("the row outlived its file", dao.getById(1L))
        verify { context.contentResolver.delete(any(), null, null) }
    }

    @Test
    fun `deleting a note whose file is already gone still drops the row`() = runBlocking {
        // The recoverable half of the ordering in the implementation: a file removed out from under us
        // must not strand an unreachable row in the list.
        val file = temporaryFolder.newFile("audio_260826_101501.m4a")
        val dao = FakeVoiceNoteDao(listOf(entityFor(file)))
        val repository = VoiceNoteRepositoryImpl(context, dao, factoryWithFreeSpace(FREE_ABOVE_THRESHOLD))
        assertTrue(file.delete())

        repository.delete(1L)

        assertNull(dao.getById(1L))
    }

    /**
     * S2495: the file and the index are one fact written twice, so the assertion checks both - a
     * rename that moved the file and left the index naming the old path is a note the list can no
     * longer open, which strategic §7 rates as this ticket's highest risk.
     */
    @Test
    fun `renaming a note moves the file and the index together`() = runBlocking {
        val file = temporaryFolder.newFile("audio_260826_101503.m4a")
        val dao = FakeVoiceNoteDao(listOf(entityFor(file)))
        val repository = VoiceNoteRepositoryImpl(context, dao, factoryWithFreeSpace(FREE_ABOVE_THRESHOLD))

        val renamed = repository.rename(1L, "shopping list.m4a")

        assertFalse("the old file survived the rename", file.exists())
        assertTrue("the new file was never written", File(file.parentFile, "shopping list.m4a").exists())
        assertEquals("shopping list.m4a", renamed?.fileName)
        assertEquals("shopping list.m4a", dao.getById(1L)?.fileName)
        assertEquals(File(file.parentFile, "shopping list.m4a").absolutePath, dao.getById(1L)?.absolutePath)
    }

    /** A name already taken is refused outright rather than suffixed onto a third name. */
    @Test
    fun `renaming onto an existing name changes neither the file nor the index`() = runBlocking {
        val file = temporaryFolder.newFile("audio_260826_101504.m4a")
        val occupied = temporaryFolder.newFile("taken.m4a")
        val dao = FakeVoiceNoteDao(listOf(entityFor(file)))
        val repository = VoiceNoteRepositoryImpl(context, dao, factoryWithFreeSpace(FREE_ABOVE_THRESHOLD))

        val renamed = repository.rename(1L, occupied.name)

        assertNull("a refused rename must report nothing renamed", renamed)
        assertTrue("the note lost its file to a name that was already taken", file.exists())
        assertEquals(file.name, dao.getById(1L)?.fileName)
    }

    @Test
    fun `recording is refused when the watch is below the headroom`() = runBlocking {
        val repository =
            VoiceNoteRepositoryImpl(context, FakeVoiceNoteDao(), factoryWithFreeSpace(FREE_BELOW_THRESHOLD))
        assertFalse(repository.hasRoomToRecord())
    }

    @Test
    fun `recording is allowed at exactly the headroom`() = runBlocking {
        // The boundary is deliberately inclusive: the headroom equals the S1861 bridge ceiling, so a
        // recording that can be started is one that can still be sent.
        val repository = VoiceNoteRepositoryImpl(context, FakeVoiceNoteDao(), factoryWithFreeSpace(THRESHOLD_BYTES))
        assertTrue(repository.hasRoomToRecord())
    }

    private companion object {
        const val THRESHOLD_BYTES = 32L * 1024L * 1024L
        const val FREE_ABOVE_THRESHOLD = THRESHOLD_BYTES * 2L
        const val FREE_BELOW_THRESHOLD = THRESHOLD_BYTES - 1L
    }
}
