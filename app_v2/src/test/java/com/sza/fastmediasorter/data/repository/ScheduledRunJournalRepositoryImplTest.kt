package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScheduledRunJournalRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var journal: ScheduledRunJournalRepositoryImpl

    @Before
    fun setup() {
        directory = File(tempFolder.root, "scheduled_run_journal")
        journal = ScheduledRunJournalRepositoryImpl(directory)
    }

    private fun record(startedAt: Long, outcome: ScheduledRunOutcome = ScheduledRunOutcome.OK, files: Int = 1) =
        ScheduledRunRecord(
            startedAt = startedAt,
            finishedAt = startedAt + 10L,
            outcome = outcome,
            filesProcessed = files,
        )

    @Test
    fun `an empty journal reads as no records`() = runTest {
        assertTrue(journal.read(1L).isEmpty())
    }

    @Test
    fun `records come back newest first`() = runTest {
        journal.append(1L, record(100L))
        journal.append(1L, record(200L))
        journal.append(1L, record(300L))

        assertEquals(listOf(300L, 200L, 100L), journal.read(1L).map { it.startedAt })
    }

    @Test
    fun `every field survives a round trip`() = runTest {
        val original = ScheduledRunRecord(
            startedAt = 1_000L,
            finishedAt = 2_500L,
            outcome = ScheduledRunOutcome.ERROR,
            filesProcessed = 7,
            message = "Failed to copy a.jpg",
            note = "Foreground service could not start: refused",
        )

        journal.append(5L, original)

        assertEquals(original, journal.read(5L).single())
    }

    @Test
    fun `an empty message and note come back as null`() = runTest {
        journal.append(5L, record(100L))

        val read = journal.read(5L).single()

        assertNull(read.message)
        assertNull(read.note)
    }

    @Test
    fun `a tab or line break in the message cannot shift the columns`() = runTest {
        journal.append(
            2L,
            ScheduledRunRecord(
                startedAt = 1L,
                finishedAt = 2L,
                outcome = ScheduledRunOutcome.ERROR,
                filesProcessed = 3,
                message = "line one\nline\ttwo",
                note = "n",
            ),
        )

        val read = journal.read(2L).single()

        assertEquals(3, read.filesProcessed)
        assertEquals("line one line two", read.message)
        assertEquals("n", read.note)
    }

    @Test
    fun `the journal keeps only the newest records`() = runTest {
        val total = ScheduledRunJournalRepositoryImpl.MAX_RECORDS + 5
        repeat(total) { journal.append(1L, record(it.toLong())) }

        val read = journal.read(1L)

        assertEquals(ScheduledRunJournalRepositoryImpl.MAX_RECORDS, read.size)
        assertEquals((total - 1).toLong(), read.first().startedAt)
        assertEquals(5L, read.last().startedAt)
    }

    @Test
    fun `schedules keep separate journals`() = runTest {
        journal.append(1L, record(100L))
        journal.append(2L, record(200L))

        assertEquals(listOf(100L), journal.read(1L).map { it.startedAt })
        assertEquals(listOf(200L), journal.read(2L).map { it.startedAt })
    }

    @Test
    fun `clear removes one schedule and leaves the others`() = runTest {
        journal.append(1L, record(100L))
        journal.append(2L, record(200L))

        journal.clear(1L)

        assertTrue(journal.read(1L).isEmpty())
        assertEquals(1, journal.read(2L).size)
    }

    @Test
    fun `clearAll removes every journal`() = runTest {
        journal.append(1L, record(100L))
        journal.append(2L, record(200L))

        journal.clearAll()

        assertTrue(journal.read(1L).isEmpty())
        assertTrue(journal.read(2L).isEmpty())
    }

    @Test
    fun `a mangled line is dropped and the rest is kept`() = runTest {
        journal.append(1L, record(100L))
        File(directory, "1.tsv").appendText("not\ta\trecord\n")
        journal.append(1L, record(300L))

        val read = journal.read(1L)

        assertEquals(listOf(300L, 100L), read.map { it.startedAt })
        assertFalse(read.any { it.startedAt == 0L })
    }
}
