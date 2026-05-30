package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * JVM coverage for [ExtractArchiveUseCase]. Exercises the local (FileInputStream / writeEntryLocal)
 * branches with real ZIPs under [TemporaryFolder]; content:// archives and SAF targets use
 * Uri / DocumentFile and are not covered here.
 */
class ExtractArchiveUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private lateinit var useCase: ExtractArchiveUseCase

    @Before
    fun setup() {
        useCase = ExtractArchiveUseCase(context)
    }

    private fun makeZip(name: String, entries: Map<String, String>): File {
        val zipFile = File(tempFolder.newFolder("zips_${name.hashCode()}"), name)
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            entries.forEach { (entryName, content) ->
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return zipFile
    }

    @Test
    fun `extracts flat entries to target directory`() = runTest {
        val zip = makeZip("flat.zip", mapOf("a.txt" to "AAA", "b.txt" to "BBB"))
        val target = tempFolder.newFolder("out")

        val events = useCase.invoke(zip.absolutePath, target.absolutePath) { false }.toList()

        assertTrue(events.first() is ExtractProgress.Started)
        val success = events.filterIsInstance<ExtractProgress.Success>().single()
        assertEquals(2, success.extractedCount)
        assertEquals("AAA", File(target, "a.txt").readText())
        assertEquals("BBB", File(target, "b.txt").readText())
        assertEquals(2, events.filterIsInstance<ExtractProgress.EntryDone>().size)
    }

    @Test
    fun `extracts nested entry creating subdirectories`() = runTest {
        val zip = makeZip("nested.zip", mapOf("sub/dir/file.txt" to "nested"))
        val target = tempFolder.newFolder("out")

        val events = useCase.invoke(zip.absolutePath, target.absolutePath) { false }.toList()

        assertTrue(events.any { it is ExtractProgress.Success })
        assertEquals("nested", File(target, "sub/dir/file.txt").readText())
    }

    @Test
    fun `path traversal entry is sanitized and skipped`() = runTest {
        val zip = makeZip("evil.zip", mapOf("../escape.txt" to "boom", "safe.txt" to "ok"))
        val target = tempFolder.newFolder("out")

        val events = useCase.invoke(zip.absolutePath, target.absolutePath) { false }.toList()

        // The ".." entry is dropped by sanitizeEntryPath; only the safe file is written.
        assertTrue(events.any { it is ExtractProgress.Success })
        assertEquals("ok", File(target, "safe.txt").readText())
        assertFalse(File(target.parentFile, "escape.txt").exists())
    }

    @Test
    fun `cancellation before first entry yields cancelled failure`() = runTest {
        val zip = makeZip("c.zip", mapOf("a.txt" to "x"))
        val target = tempFolder.newFolder("out")

        val events = useCase.invoke(zip.absolutePath, target.absolutePath) { true }.toList()

        val failure = events.filterIsInstance<ExtractProgress.Failure>().single()
        assertEquals("cancelled", failure.error)
    }

    @Test
    fun `nonexistent archive yields extract_error failure`() = runTest {
        val target = tempFolder.newFolder("out")
        val missing = File(tempFolder.root, "missing.zip").absolutePath

        val events = useCase.invoke(missing, target.absolutePath) { false }.toList()

        val failure = events.filterIsInstance<ExtractProgress.Failure>().single()
        assertEquals("extract_error", failure.error)
    }
}
