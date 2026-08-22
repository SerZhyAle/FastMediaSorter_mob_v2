package com.sza.fastmediasorter.core.logging

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * S1802: the zip is the last place the watch log can silently vanish, and the helper had no test
 * before this ticket - so these also pin the pre-existing behaviour the extension had to preserve.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class LogExportHelperTest {

    // RuntimeEnvironment, not ApplicationProvider: androidx.test:core is an androidTest dependency
    // here, so the instrumentation helper does not exist on the unit-test classpath.
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun clearCachedZip() {
        File(context.cacheDir, ZIP_NAME).delete()
        // S1806: the export now walks the log directory itself, so a report left there by one case
        // would leak into the next one - including the case that asserts an empty set produces none.
        LoggingHelper.getLogsDirectory(context).listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `an extra file is carried into the zip under its own name`() {
        val extra = File(context.cacheDir, "watch_log_20260818_120000.txt").apply {
            writeText("watch line")
        }

        LogExportHelper.buildLogsZipUri(context, listOf(extra))

        assertTrue("no zip was produced", zipFile().exists())
        assertTrue("the extra file is missing from the zip", entryNames().contains(extra.name))
    }

    @Test
    fun `an extra file that does not exist is skipped without failing the zip`() {
        val present = File(context.cacheDir, "watch_log_present.txt").apply { writeText("here") }
        val absent = File(context.cacheDir, "watch_log_absent.txt")
        absent.delete()

        LogExportHelper.buildLogsZipUri(context, listOf(present, absent))

        assertTrue("no zip was produced", zipFile().exists())
        assertTrue("the present file is missing", entryNames().contains(present.name))
        assertFalse("the absent file was written as an entry", entryNames().contains(absent.name))
    }

    @Test
    fun `with no extra file the zip carries only what it carried before`() {
        // No log tree is planted in a JVM test, so LoggingHelper.getLogFiles() is empty and today's
        // behaviour is "nothing to zip" - the extension must not turn that into an empty archive.
        val uri = LogExportHelper.buildLogsZipUri(context)

        assertTrue(
            "an empty log set produced a zip",
            uri == null && !zipFile().exists()
        )
    }

    @Test
    fun `a watch report in the log directory reaches the zip with no extra argument`() {
        val report = writeWatchReport("watch_log_20260819_010000.txt")

        LogExportHelper.buildLogsZipUri(context)

        assertTrue("no zip was produced", zipFile().exists())
        assertTrue("the stored watch report is missing", entryNames().contains(report.name))
    }

    @Test
    fun `the report the notification passes is not written twice`() {
        val report = writeWatchReport("watch_log_20260819_020000.txt")

        LogExportHelper.buildLogsZipUri(context, listOf(report))

        assertTrue("no zip was produced", zipFile().exists())
        assertEquals("the same file was zipped twice", 1, entryNames().count { it == report.name })
    }

    @Test
    fun `a directory holding only watch reports still exports`() {
        // No log tree is planted in a JVM test, so the phone's own set is empty here: this is the
        // "watch report alone is worth sending" case, which used to answer "no logs". The assertion
        // is on the archive rather than on the returned URI: FileProvider does not resolve one under
        // Robolectric, so a null URI here says nothing about whether the export refused.
        val report = writeWatchReport("watch_log_20260819_030000.txt")

        LogExportHelper.buildLogsZipUri(context)

        assertTrue("an archive with only a watch report was refused", zipFile().exists())
        assertTrue("the only file present was left out", entryNames().contains(report.name))
    }

    private fun writeWatchReport(name: String): File {
        val directory = LoggingHelper.getLogsDirectory(context)
        directory.mkdirs()
        return File(directory, name).apply { writeText("watch line") }
    }

    private fun zipFile() = File(context.cacheDir, ZIP_NAME)

    private fun entryNames(): List<String> =
        ZipFile(zipFile()).use { zip -> zip.entries().toList().map { it.name } }

    private companion object {
        const val ZIP_NAME = "fastmediasorter_logs.zip"
    }
}
