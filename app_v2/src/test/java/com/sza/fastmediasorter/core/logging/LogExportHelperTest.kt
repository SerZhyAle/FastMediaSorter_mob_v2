package com.sza.fastmediasorter.core.logging

import android.content.Context
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
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
        archives().forEach { it.delete() }
        // S1806: the export now walks the log directory itself, so a report left there by one case
        // would leak into the next one - including the case that asserts an empty set produces none.
        LoggingHelper.getLogsDirectory(context).listFiles()?.forEach { it.delete() }
        PrivateDiagnosticValues.clear()
    }

    @After
    fun clearRegisteredValues() {
        PrivateDiagnosticValues.clear()
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

    @Test
    fun `a file at or under the ceiling is packed whole`() {
        val file = File(context.cacheDir, "session_small.txt").apply { writeText("0123456789") }
        val out = ByteArrayOutputStream()

        LogExportHelper.writeBounded(file, out, ceilingBytes = 10, headBytes = 2, tailBytes = 3)

        assertEquals("0123456789", out.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `a file above the ceiling keeps head and tail around the truncation marker`() {
        val file = File(context.cacheDir, "session_big.txt").apply { writeText("HEADmiddle-bytesTAIL") }
        val out = ByteArrayOutputStream()

        LogExportHelper.writeBounded(file, out, ceilingBytes = 10, headBytes = 4, tailBytes = 4)

        val expected = "HEAD\n[Diag] LOG TRUNCATED | dropped_middle_bytes=12" +
            " | kept_head_bytes=4 | kept_tail_bytes=4\nTAIL"
        assertEquals(expected, out.toString(Charsets.UTF_8.name()))
    }

    // DIAGNOSTIC-REPORT 0.13, section 10: a value learned after the line was written is still redacted at export.
    @Test
    fun `a registered exchange value is redacted again when the bundle is packed`() {
        val file = File(context.cacheDir, "session_exchange.txt").apply {
            writeText("I/Tunnel: unreachable relay.example.net\nI/Tunnel: retry\n")
        }
        PrivateDiagnosticValues.register("relay.example.net")
        val out = ByteArrayOutputStream()

        LogExportHelper.writeRedacted(file, out, context.cacheDir)

        assertEquals("I/Tunnel: unreachable [REDACTED]\nI/Tunnel: retry\n", out.toString(Charsets.UTF_8.name()))
        assertTrue("the original log was changed", file.readText().contains("relay.example.net"))
    }

    @Test
    fun `with nothing registered the export packs the file byte for byte`() {
        val file = File(context.cacheDir, "session_plain.txt").apply { writeText("no trailing newline") }
        val out = ByteArrayOutputStream()

        LogExportHelper.writeRedacted(file, out, context.cacheDir)

        assertEquals("no trailing newline", out.toString(Charsets.UTF_8.name()))
    }

    private fun writeWatchReport(name: String): File {
        val directory = LoggingHelper.getLogsDirectory(context)
        directory.mkdirs()
        return File(directory, name).apply { writeText("watch line") }
    }

    // DIAGNOSTIC-REPORT section 8 A: the name carries the version and the export time, so the file is found by pattern.
    @Test
    fun `the archive follows the contract name and starts with the environment summary`() {
        writeWatchReport("watch_log_20260820_010000.txt")

        LogExportHelper.buildLogsZipUri(context)

        val name = zipFile().name
        assertTrue("the name does not follow the pattern: $name", ARCHIVE_NAME_PATTERN.matches(name))
        assertEquals(
            "the environment summary is not the first entry",
            DiagnosticArchive.ENVIRONMENT_ENTRY,
            entryNames().first()
        )
        val summary = entryText(DiagnosticArchive.ENVIRONMENT_ENTRY).lines()
        assertTrue("schema_version is missing", summary.contains("schema_version=1"))
        assertTrue("app_version is missing", summary.any { it.startsWith("app_version=") })
        assertTrue("generated_utc is missing", summary.any { it.startsWith("generated_utc=") })
    }

    @Test
    fun `an export removes the archives of earlier exports and the fixed-name one`() {
        writeWatchReport("watch_log_20260820_020000.txt")
        val earlier = File(context.cacheDir, "FastMediaSorter-logs-old-20200101-000000.zip").apply { writeText("x") }
        val legacy = File(context.cacheDir, DiagnosticArchive.LEGACY_FILE_NAME).apply { writeText("x") }

        LogExportHelper.buildLogsZipUri(context)

        assertFalse("the earlier archive was kept", earlier.exists())
        assertFalse("the fixed-name archive was kept", legacy.exists())
        assertEquals("more than one archive is left", 1, archives().size)
    }

    @Test
    fun `the file name carries the version and the local export time`() {
        val moment = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 14, 5, 9) }.time

        assertEquals(
            "FastMediaSorter-logs-26.1115.1200-20261115-140509.zip",
            DiagnosticArchive.fileName("26.1115.1200", moment)
        )
        assertEquals(
            "FastMediaSorter-logs-26.1115.1200-Photos_beta-20261115-140509.zip",
            DiagnosticArchive.fileName("26.1115.1200-Photos beta", moment)
        )
        assertTrue(DiagnosticArchive.isArchiveName(DiagnosticArchive.fileName("1.0", moment)))
        assertFalse(DiagnosticArchive.isArchiveName(DiagnosticArchive.LEGACY_FILE_NAME))
    }

    private fun archives(): List<File> =
        context.cacheDir.listFiles { file -> DiagnosticArchive.isArchiveName(file.name) }?.toList().orEmpty()

    // An absent archive still gives a File, so the existence assertions above keep their meaning.
    private fun zipFile(): File = archives().firstOrNull() ?: File(context.cacheDir, "no-archive-was-written.zip")

    private fun entryNames(): List<String> =
        ZipFile(zipFile()).use { zip -> zip.entries().toList().map { it.name } }

    private fun entryText(name: String): String =
        ZipFile(zipFile()).use { zip -> zip.getInputStream(zip.getEntry(name)).bufferedReader().readText() }

    private companion object {
        val ARCHIVE_NAME_PATTERN = Regex("""FastMediaSorter-logs-[A-Za-z0-9._-]+-\d{8}-\d{6}\.zip""")
    }
}
