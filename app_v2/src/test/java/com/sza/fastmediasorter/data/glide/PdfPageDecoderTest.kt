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
import java.io.File
import java.io.FileNotFoundException

// A SAF document URI is long by construction; naming its constant prefix keeps the cases readable.
private const val SAF_DOC_PREFIX =
    "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2F"

/**
 * S2395: tests for [PdfPageDecoder] supporting both direct filesystem paths and SAF content URIs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfPageDecoderTest {

    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true) {
        every { this@mockk.contentResolver } returns this@PdfPageDecoderTest.contentResolver
    }
    private val decoder = PdfPageDecoder(context)
    private val options = Options()

    @Test
    fun `handles returns true for regular pdf file`() {
        val file = File("/storage/emulated/0/Download/document.pdf")
        assertTrue(decoder.handles(file, options))
    }

    @Test
    fun `handles returns true for uppercase PDF extension`() {
        val file = File("/storage/emulated/0/Download/document.PDF")
        assertTrue(decoder.handles(file, options))
    }

    @Test
    fun `handles returns true for SAF content URI ending in pdf`() {
        val safUri = "${SAF_DOC_PREFIX}test.pdf"
        val file = File(safUri)
        assertTrue(decoder.handles(file, options))
    }

    @Test
    fun `handles returns false for non-pdf file`() {
        val file = File("/storage/emulated/0/Download/image.jpg")
        assertFalse(decoder.handles(file, options))
    }

    @Test
    fun `handles returns false for SAF content URI of epub or image`() {
        val safEpub = "${SAF_DOC_PREFIX}book.epub"
        assertFalse(decoder.handles(File(safEpub), options))
    }

    @Test
    fun `decode returns null when contentResolver returns null descriptor`() {
        val safUri = "${SAF_DOC_PREFIX}missing.pdf"
        val uri = Uri.parse(safUri)
        every { contentResolver.openFileDescriptor(uri, "r") } returns null

        val result = decoder.decode(File(safUri), 100, 100, options)
        assertNull(result)
        verify { contentResolver.openFileDescriptor(uri, "r") }
    }

    @Test
    fun `decode catches exception when opening content URI fails`() {
        val safUri = "${SAF_DOC_PREFIX}error.pdf"
        val uri = Uri.parse(safUri)
        every { contentResolver.openFileDescriptor(uri, "r") } throws FileNotFoundException("open failed")

        val result = decoder.decode(File(safUri), 100, 100, options)
        assertNull(result)
        verify { contentResolver.openFileDescriptor(uri, "r") }
    }
}
