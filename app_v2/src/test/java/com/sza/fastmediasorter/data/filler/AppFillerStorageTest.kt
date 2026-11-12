package com.sza.fastmediasorter.data.filler

import com.sza.fastmediasorter.data.repository.StorageVolumeSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFillerStorageTest {
    private val context = RuntimeEnvironment.getApplication()
    private val volumes = mockk<StorageVolumeSource>()
    private val journalScope = CoroutineScope(Dispatchers.Unconfined)
    private lateinit var journal: FillerSessionJournal
    private lateinit var storage: AppFillerStorage

    @Before
    fun setUp() {
        File(context.noBackupFilesDir, "filler-sessions.json").delete()
        File(context.noBackupFilesDir, "filler-sessions.json.bak").delete()
        File(context.noBackupFilesDir, "filler-sessions.json.new").delete()
        every { volumes.listVolumes() } returns emptyList()
        journal = journalOf()
        storage = AppFillerStorage(context, volumes, journal)
    }

    @Test
    fun `default location can be used without a document grant`() = runTest {
        assertNotNull(storage.defaultLocation())
        val place = requireNotNull(storage.place())
        assertEquals(storage.defaultLocation(), place.target.location)
        assertTrue(storage.validate(place.target))
    }

    @Test
    fun `interrupted create is cleaned and unrelated file is preserved`() = runTest {
        val target = requireNotNull(storage.place()).target
        val session = journal.begin(target)
        journal.prepareCreate(session, 0)
        val file = storage.create(session, 0)
        storage.append(file, 8)
        val directory = File(requireNotNull(journal.entry(session).directory))
        val foreign = File(directory, "user-file.txt")
        foreign.writeText("keep")

        assertFalse(storage.cleanup(session))
        assertTrue(foreign.exists())
        assertFalse(File(requireNotNull(android.net.Uri.parse(file.id).path)).exists())
        assertEquals(null, journal.entry(session).pendingIndex)

        foreign.delete()
        assertTrue(storage.cleanup(session))
        journal.finish(session)
        assertTrue(journal.pendingSessions().isEmpty())
    }

    @After
    fun tearDown() = runBlocking { journalScope.coroutineContext.job.cancelAndJoin() }

    private fun journalOf() = FillerSessionJournal(context, journalScope)
}
