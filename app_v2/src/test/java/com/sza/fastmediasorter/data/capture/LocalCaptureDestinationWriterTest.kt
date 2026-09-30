package com.sza.fastmediasorter.data.capture

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.local.LocalSink
import com.sza.fastmediasorter.utils.SafHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class LocalCaptureDestinationWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val writer = LocalCaptureDestinationWriter(
        mock(Context::class.java),
        LocalDestinationClassifier(),
        mock(LocalDestinationWriter::class.java),
    )

    @Test
    fun `filesystem path does not select SAF`() {
        assertFalse(writer.isSafDestination("/storage/emulated/0/Movies"))
    }

    @Test
    fun `canonical content URI selects SAF`() {
        assertTrue(writer.isSafDestination("content://provider/tree/primary%3AMovies"))
    }

    @Test
    fun `malformed content URI normalizes before SAF access`() {
        assertEquals(
            "content://provider/tree/primary%3AMovies",
            writer.normalizeSafDestination("content:/provider/tree/primary%3AMovies"),
        )
    }

    @Test
    fun `file path collision gets the contract ordinal instead of overwriting`() = runTest {
        val dir = tempFolder.newFolder("dest")
        File(dir, "photo_260821_123456.jpg").writeText("existing")
        val recording = RecordingWriter()
        val collisionWriter = LocalCaptureDestinationWriter(
            mock(Context::class.java),
            PlainPathClassifier(),
            recording,
        )

        val saved = collisionWriter.writeCapture(
            tempFolder.newFile("temp.jpg"),
            dir.absolutePath,
            "photo_260821_123456.jpg",
        ).getOrThrow()

        assertEquals("photo_260821_123456 (2).jpg", saved.displayName)
        assertEquals(File(dir, "photo_260821_123456 (2).jpg").absolutePath, recording.openedPath)
    }

    @After
    fun releaseSafHelper() {
        unmockkObject(SafHelper)
    }

    @Test
    fun `SAF collision probes one tree listing and takes the contract ordinal`() = runTest {
        val root = safRootWith("clip.mp4", "clip (2).mp4")
        val document = mockk<DocumentFile> { every { uri } returns mockk<Uri>() }
        stubSafHelper(root, document)
        val resolver = mockk<ContentResolver> {
            every { openOutputStream(any(), "w") } returns ByteArrayOutputStream()
        }

        val saved = safWriter(resolver).writeCapture(tempFolder.newFile("temp.mp4"), SAF_TREE, "clip.mp4")
            .getOrThrow()

        assertEquals("clip (3).mp4", saved.displayName)
        verify(exactly = 1) { root.listFiles() }
        verify(exactly = 0) { root.findFile(any()) }
    }

    @Test
    fun `SAF copy failure deletes the created document`() = runTest {
        val document = mockk<DocumentFile> {
            every { uri } returns mockk<Uri>()
            every { delete() } returns true
        }
        stubSafHelper(safRootWith(), document)
        val resolver = mockk<ContentResolver> { every { openOutputStream(any(), "w") } returns null }

        val result = safWriter(resolver).writeCapture(tempFolder.newFile("temp.mp4"), SAF_TREE, "clip.mp4")

        assertTrue(result.isFailure)
        verify(exactly = 1) { document.delete() }
    }

    private fun safRootWith(vararg names: String): DocumentFile {
        val children = names.map { childName -> mockk<DocumentFile> { every { name } returns childName } }
        return mockk(relaxed = true) { every { listFiles() } returns children.toTypedArray() }
    }

    private fun stubSafHelper(root: DocumentFile, document: DocumentFile) {
        mockkObject(SafHelper)
        // The default mimeType argument reads MimeTypeMap, which a plain JVM test lacks.
        every { SafHelper.guessMimeType(any()) } returns "video/mp4"
        every { SafHelper.getTreeRoot(any(), any()) } returns root
        every { SafHelper.getOrCreateWritableChildFile(any(), any(), any(), any(), any()) } returns document
    }

    private fun safWriter(resolver: ContentResolver) = LocalCaptureDestinationWriter(
        mockk<Context> { every { contentResolver } returns resolver },
        LocalDestinationClassifier(),
        mock(LocalDestinationWriter::class.java),
    )

    // The real classifier resolves MIME types through MimeTypeMap, which a plain JVM test lacks.
    private class PlainPathClassifier : LocalDestinationClassifier() {
        override fun classify(absolutePath: String): LocalDestinationCategory =
            LocalDestinationCategory.NonPublic(absolutePath, File(absolutePath).name, "image/jpeg")
    }

    private class RecordingWriter : LocalDestinationWriter {
        var openedPath: String? = null

        override suspend fun open(destination: LocalDestinationCategory, overwrite: Boolean): Result<LocalSink> {
            val path = (destination as LocalDestinationCategory.NonPublic).absolutePath
            openedPath = path
            return Result.success(object : LocalSink {
                override val outputStream: OutputStream = ByteArrayOutputStream()
                override suspend fun commit(): Result<String> = Result.success(path)
                override suspend fun abort() = Unit
            })
        }
    }

    private companion object {
        const val SAF_TREE = "content://provider/tree/primary%3AMovies"
    }
}
