package com.sza.fastmediasorter.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S1805: the retention rule is a claim about a directory holding two kinds of file, so the cases
 * here put both kinds in it - a test on watch reports alone cannot tell a name filter from none.
 */
class LogFilePruneTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `keeps the newest reports and deletes the rest`() {
        val reports = (1..REPORT_COUNT).map { index -> writeStamped("$WATCH_PREFIX$index$WATCH_SUFFIX", index) }

        LoggingHelper.pruneLogFiles(folder.root, WATCH_PREFIX, WATCH_SUFFIX)

        val survivors = reports.filter { it.exists() }.map { it.name }.sorted()
        val expected = (REPORT_COUNT - LoggingHelper.MAX_LOG_FILES + 1..REPORT_COUNT)
            .map { index -> "$WATCH_PREFIX$index$WATCH_SUFFIX" }
        assertEquals(expected, survivors)
    }

    @Test
    fun `leaves the phone's own logs alone`() {
        val phoneLogs = (1..PHONE_LOG_COUNT).map { index -> writeStamped("fastmediasorter_$index.log", index) }
        (1..REPORT_COUNT).forEach { index -> writeStamped("$WATCH_PREFIX$index$WATCH_SUFFIX", index) }

        LoggingHelper.pruneLogFiles(folder.root, WATCH_PREFIX, WATCH_SUFFIX)

        assertTrue(phoneLogs.all { it.exists() })
        val remainingReports = folder.root.listFiles { file -> file.name.startsWith(WATCH_PREFIX) }?.size
        assertEquals(LoggingHelper.MAX_LOG_FILES, remainingReports)
    }

    @Test
    fun `deletes nothing when the directory holds fewer files than the limit`() {
        val reports = (1..PHONE_LOG_COUNT).map { index -> writeStamped("$WATCH_PREFIX$index$WATCH_SUFFIX", index) }

        LoggingHelper.pruneLogFiles(folder.root, WATCH_PREFIX, WATCH_SUFFIX)

        assertTrue(reports.all { it.exists() })
    }

    @Test
    fun `an empty directory is left as it is`() {
        LoggingHelper.pruneLogFiles(folder.root, WATCH_PREFIX, WATCH_SUFFIX)

        assertEquals(0, folder.root.listFiles()?.size)
    }

    /** Modification times are set explicitly: files written in one test run share a timestamp. */
    private fun writeStamped(name: String, ageIndex: Int): File =
        folder.newFile(name).apply {
            writeText(name)
            setLastModified(BASE_MILLIS + ageIndex * STEP_MILLIS)
        }

    private companion object {
        const val WATCH_PREFIX = "watch_log_"
        const val WATCH_SUFFIX = ".txt"
        const val REPORT_COUNT = 7
        const val PHONE_LOG_COUNT = 3
        const val BASE_MILLIS = 1_600_000_000_000L
        const val STEP_MILLIS = 60_000L
    }
}
