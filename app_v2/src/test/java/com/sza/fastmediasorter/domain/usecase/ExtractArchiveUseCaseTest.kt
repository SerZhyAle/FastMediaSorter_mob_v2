package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
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
        useCase = ExtractArchiveUseCase(context, mockk(relaxed = true))
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

    private fun createEncryptedZip(name: String, password: CharArray, entries: Map<String, String>): File {
        val zipFile = File(tempFolder.newFolder("encrypted_${name.hashCode()}"), name)
        val archive = ZipFile(zipFile, password)
        entries.forEach { (entryName, content) ->
            val parameters = ZipParameters().apply {
                fileNameInZip = entryName
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            }
            archive.addStream(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)), parameters)
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

    @Test
    fun `encrypted archive extracts with correct password`() = runTest {
        val password = "secret".toCharArray()
        val zip = createEncryptedZip("protected.zip", password, mapOf("a.txt" to "AAA"))
        val target = tempFolder.newFolder("out")

        assertTrue(useCase.isPasswordRequired(zip.absolutePath))
        assertEquals(ArchiveAccessResult.Accessible, useCase.validateArchiveAccess(zip.absolutePath, password))
        val events = useCase.invoke(zip.absolutePath, target.absolutePath, password) { false }.toList()

        val success = events.filterIsInstance<ExtractProgress.Success>().single()
        assertEquals(1, success.extractedCount)
        assertEquals("AAA", File(target, "a.txt").readText())
    }

    @Test
    fun `encrypted archive without password yields password_required failure`() = runTest {
        val zip = createEncryptedZip("missing-password.zip", "secret".toCharArray(), mapOf("a.txt" to "AAA"))
        val target = tempFolder.newFolder("out")

        assertEquals(ArchiveAccessResult.PasswordRequired, useCase.validateArchiveAccess(zip.absolutePath, null))
        val events = useCase.invoke(zip.absolutePath, target.absolutePath) { false }.toList()

        val failure = events.filterIsInstance<ExtractProgress.Failure>().single()
        assertEquals("password_required", failure.error)
    }

    @Test
    fun `encrypted archive with wrong password yields password_invalid failure`() = runTest {
        val zip = createEncryptedZip("wrong-password.zip", "secret".toCharArray(), mapOf("a.txt" to "AAA"))
        val target = tempFolder.newFolder("out")
        val wrongPassword = "wrong".toCharArray()

        assertEquals(ArchiveAccessResult.InvalidPassword, useCase.validateArchiveAccess(zip.absolutePath, wrongPassword))
        val events = useCase.invoke(zip.absolutePath, target.absolutePath, wrongPassword) { false }.toList()

        val failure = events.filterIsInstance<ExtractProgress.Failure>().single()
        assertEquals("password_invalid", failure.error)
    }

    @Test
    fun `encrypted archive skips path traversal entries`() = runTest {
        val password = "secret".toCharArray()
        val zip = createEncryptedZip("encrypted-evil.zip", password, mapOf("../escape.txt" to "boom", "safe.txt" to "ok"))
        val target = tempFolder.newFolder("out")

        val events = useCase.invoke(zip.absolutePath, target.absolutePath, password) { false }.toList()

        assertTrue(events.any { it is ExtractProgress.Success })
        assertEquals("ok", File(target, "safe.txt").readText())
        assertFalse(File(target.parentFile, "escape.txt").exists())
    }
}
