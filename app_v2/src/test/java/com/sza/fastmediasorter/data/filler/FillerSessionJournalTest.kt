package com.sza.fastmediasorter.data.filler

import com.sza.fastmediasorter.domain.filler.FillerFile
import com.sza.fastmediasorter.domain.filler.FillerTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class FillerSessionJournalTest {
    private val context = RuntimeEnvironment.getApplication()
    private val target = FillerTarget("content://local/tree", "primary")
    private lateinit var journal: FillerSessionJournal

    @Before
    fun setUp() {
        File(context.noBackupFilesDir, JOURNAL_FILE).delete()
        File(context.noBackupFilesDir, "$JOURNAL_FILE.bak").delete()
        File(context.noBackupFilesDir, "$JOURNAL_FILE.new").delete()
        journal = journalOf()
    }

    @Test
    fun `pending create survives a new journal instance`() = runTest {
        val session = journal.begin(target)
        journal.setDirectory(session, "content://local/session")
        journal.prepareCreate(session, INDEX)

        val reopened = journalOf()
        val entry = reopened.entry(session)
        assertEquals(listOf(session), reopened.pendingSessions())
        assertEquals(INDEX, entry.pendingIndex)
        assertEquals("content://local/session", entry.directory)
        assertTrue(entry.files.isEmpty())
    }

    @Test
    fun `record and cleanup survive reopen and finish only when empty`() = runTest {
        val session = journal.begin(target)
        journal.setDirectory(session, "content://local/session")
        journal.prepareCreate(session, INDEX)
        journal.recordCreated(session, INDEX, FillerFile("content://local/file"))
        val reopened = journalOf()

        assertEquals(listOf("content://local/file"), reopened.entry(session).files)
        assertNull(reopened.entry(session).pendingIndex)
        assertTrue(runCatching { reopened.finish(session) }.isFailure)
        reopened.forgetFile(session, "content://local/file")
        reopened.setDirectory(session, null)
        reopened.finish(session)
        assertTrue(journalOf().pendingSessions().isEmpty())
    }

    @Test
    fun `interrupted atomic update restores the previous record`() = runTest {
        val session = journal.begin(target)
        val path = File(context.noBackupFilesDir, JOURNAL_FILE)
        val backup = File(context.noBackupFilesDir, "$JOURNAL_FILE.bak")
        backup.writeBytes(path.readBytes())
        path.writeText("interrupted")

        val reopened = journalOf()
        assertEquals(listOf(session), reopened.pendingSessions())
        assertNull(reopened.entry(session).pendingIndex)
    }

    @Test
    fun `invalid journal is not replaced by an empty one`() = runTest {
        val path = File(context.noBackupFilesDir, JOURNAL_FILE)
        path.writeText("not a journal")

        assertTrue(runCatching { journal.pendingSessions() }.isFailure)
        assertTrue(runCatching { journal.begin(target) }.isFailure)
        assertEquals("not a journal", path.readText())
    }

    @Test
    fun `mismatched creation cannot be recorded`() = runTest {
        val session = journal.begin(target)
        journal.prepareCreate(session, INDEX)

        assertTrue(runCatching { journal.recordCreated(session, OTHER_INDEX, FillerFile("untrusted")) }.isFailure)
        assertEquals(INDEX, journal.entry(session).pendingIndex)
        assertFalse(journal.entry(session).files.contains("untrusted"))
    }

    @Test
    fun `hasPending turns true when a session begins`() = runTest {
        journal.begin(target)

        assertTrue(journal.hasPending.value)
    }

    @Test
    fun `hasPending turns false after a clean finish`() = runTest {
        val session = journal.begin(target)
        journal.finish(session)

        assertFalse(journal.hasPending.value)
    }

    // Real time on purpose: the first read runs on the IO dispatcher, which virtual test time cannot wait for.
    @Test
    fun `a reopened journal reports the pending session it finds on disk`() = runBlocking {
        journal.begin(target)

        val reopened = journalOf()
        assertTrue(withTimeout(TIMEOUT_MS) { reopened.hasPending.first { it } })
    }

    @Test
    fun `an unreadable journal reports pending cleanup`() = runBlocking {
        File(context.noBackupFilesDir, JOURNAL_FILE).writeText("not a journal")

        val reopened = journalOf()
        assertTrue(withTimeout(TIMEOUT_MS) { reopened.hasPending.first { it } })
    }

    private fun journalOf() = FillerSessionJournal(context, CoroutineScope(Dispatchers.Unconfined))

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val JOURNAL_FILE = "filler-sessions.json"
        const val INDEX = 1
        const val OTHER_INDEX = 2
    }
}
