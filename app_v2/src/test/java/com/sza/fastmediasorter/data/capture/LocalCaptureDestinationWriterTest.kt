package com.sza.fastmediasorter.data.capture

import android.content.Context
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.local.LocalSink
import kotlinx.coroutines.test.runTest
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
}
