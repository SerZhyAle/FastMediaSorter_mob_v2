package com.sza.fastmediasorter.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * S4385: crash reports satisfy the session name pattern too, so the session prune used to count
 * them and a crash loop evicted both earlier sessions and earlier crash reports. This pins the
 * split: the session prune counts sessions only and never deletes a crash report, while the crash
 * pool prunes by its own count and never touches a session.
 */
class LoggingHelperSessionPruneTest {

    @Test
    fun `session prune keeps the newest nine sessions and every crash report`() {
        val dir = newLogDir()
        seed(dir, LoggingHelper.CRASH_FILE_PREFIX, CRASHES_OLD, BASE_MILLIS)
        seed(dir, LoggingHelper.LOG_FILE_PREFIX, SESSIONS_SEEDED, BASE_MILLIS + POOL_OFFSET_MILLIS)

        LoggingHelper.pruneSessionLogs(dir)

        val leftovers = dir.listNames()
        assertEquals(RETAINED_PER_POOL, leftovers.sessionCount())
        assertEquals(CRASHES_OLD, leftovers.crashCount())
    }

    @Test
    fun `session prune evicts the oldest sessions and keeps the newest`() {
        val dir = newLogDir()
        seed(dir, LoggingHelper.LOG_FILE_PREFIX, SESSIONS_SEEDED, BASE_MILLIS)

        LoggingHelper.pruneSessionLogs(dir)

        val leftovers = dir.listNames()
        assertEquals(RETAINED_PER_POOL, leftovers.sessionCount())
        assertTrue("oldest session file must be evicted", oldestName(LoggingHelper.LOG_FILE_PREFIX) !in leftovers)
        assertTrue("newest session file must survive", newestSessionName() in leftovers)
    }

    @Test
    fun `crash pool prunes by its own count and never touches sessions`() {
        val dir = newLogDir()
        seed(dir, LoggingHelper.LOG_FILE_PREFIX, SESSIONS_SMALL, BASE_MILLIS)
        seed(dir, LoggingHelper.CRASH_FILE_PREFIX, CRASHES_SEEDED, BASE_MILLIS + POOL_OFFSET_MILLIS)

        LoggingHelper.pruneLogFiles(
            dir,
            LoggingHelper.CRASH_FILE_PREFIX,
            LoggingHelper.LOG_FILE_SUFFIX,
            LoggingHelper.MAX_LOG_FILES - 1
        )

        val leftovers = dir.listNames()
        assertEquals(RETAINED_PER_POOL, leftovers.crashCount())
        assertEquals(SESSIONS_SMALL, leftovers.sessionCount())
        assertTrue("oldest crash report must be evicted", oldestName(LoggingHelper.CRASH_FILE_PREFIX) !in leftovers)
    }

    private fun newLogDir(): File = Files.createTempDirectory("fms-s4385").toFile()

    private fun oldestName(prefix: String): String = "${prefix}0${LoggingHelper.LOG_FILE_SUFFIX}"

    private fun newestSessionName(): String =
        "${LoggingHelper.LOG_FILE_PREFIX}${SESSIONS_SEEDED - 1}${LoggingHelper.LOG_FILE_SUFFIX}"

    /**
     * Creates [count] files under [prefix] with distinct, explicitly set modification times, so the
     * newest-first sort the prune relies on never depends on filesystem timestamp granularity.
     */
    private fun seed(dir: File, prefix: String, count: Int, baseMillis: Long) {
        repeat(count) { index ->
            val file = File(dir, "$prefix$index${LoggingHelper.LOG_FILE_SUFFIX}")
            file.writeText("seed")
            assertTrue(file.setLastModified(baseMillis + index * STAMP_STEP_MILLIS))
        }
    }

    private fun File.listNames(): List<String> = listFiles()?.map { it.name }.orEmpty()

    private fun List<String>.sessionCount(): Int =
        count { !it.startsWith(LoggingHelper.CRASH_FILE_PREFIX) }

    private fun List<String>.crashCount(): Int =
        count { it.startsWith(LoggingHelper.CRASH_FILE_PREFIX) }

    private companion object {
        const val BASE_MILLIS = 1_700_000_000_000L
        const val POOL_OFFSET_MILLIS = 10_000_000L
        const val STAMP_STEP_MILLIS = 60_000L
        const val SESSIONS_SEEDED = 12
        const val SESSIONS_SMALL = 5
        const val CRASHES_SEEDED = 11
        const val CRASHES_OLD = 3
        const val RETAINED_PER_POOL = LoggingHelper.MAX_LOG_FILES - 1
    }
}
