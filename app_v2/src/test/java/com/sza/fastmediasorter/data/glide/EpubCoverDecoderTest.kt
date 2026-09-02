package com.sza.fastmediasorter.data.glide

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.bumptech.glide.load.Options
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException

// A SAF document URI is long by construction; naming its constant prefix keeps the cases readable.
private const val SAF_DOC_PREFIX =
    "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2F"

/**
 * S2395: tests for [EpubCoverDecoder] supporting both direct filesystem paths and SAF content URIs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubCoverDecoderTest {

    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true) {
        every { this@mockk.contentResolver } returns this@EpubCoverDecoderTest.contentResolver
    }
    private val decoder = EpubCoverDecoder(context)
    private val options = Options()

    @Test
    fun `handles returns true for regular epub file`() {
        val file = File("/storage/emulated/0/Download/book.epub")
        assertTrue(decoder.handles(file, options))
    }

    @Test
    fun `handles returns true for uppercase EPUB extension`() {
        val file = File("/storage/emulated/0/Download/book.EPUB")
        assertTrue(decoder.handles(file, options))
    }

    @Test
    fun `handles returns true for SAF content URI ending in epub`() {
        val safUri = "${SAF_DOC_PREFIX}book.epub"
        val file = File(safUri)
        assertTrue(decoder.handles(file, options))
    }

    @Test
    fun `handles returns false for non-epub file`() {
        val file = File("/storage/emulated/0/Download/image.jpg")
        assertFalse(decoder.handles(file, options))
    }

    @Test
    fun `handles returns false for SAF content URI of pdf`() {
        val safPdf = "${SAF_DOC_PREFIX}test.pdf"
        assertFalse(decoder.handles(File(safPdf), options))
    }

    @Test
    fun `decode returns null when contentResolver returns null stream`() {
        val safUri = "${SAF_DOC_PREFIX}missing.epub"
        val uri = Uri.parse(safUri)
        every { contentResolver.openInputStream(uri) } returns null

        val result = decoder.decode(File(safUri), 100, 100, options)
        assertNull(result)
        verify { contentResolver.openInputStream(uri) }
    }

    @Test
    fun `decode catches exception when opening content URI fails`() {
        val safUri = "${SAF_DOC_PREFIX}error.epub"
        val uri = Uri.parse(safUri)
        every { contentResolver.openInputStream(uri) } throws FileNotFoundException("open failed")

        val result = decoder.decode(File(safUri), 100, 100, options)
        assertNull(result)
        verify { contentResolver.openInputStream(uri) }
    }

    @Test
    fun `decode handles corrupted epub stream gracefully`() {
        val safUri = "${SAF_DOC_PREFIX}corrupt.epub"
        val uri = Uri.parse(safUri)
        // Provide invalid byte stream
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(byteArrayOf(0, 1, 2, 3)) }

        val result = decoder.decode(File(safUri), 100, 100, options)
        assertNull(result)
        verify { contentResolver.openInputStream(uri) }
    }
}
