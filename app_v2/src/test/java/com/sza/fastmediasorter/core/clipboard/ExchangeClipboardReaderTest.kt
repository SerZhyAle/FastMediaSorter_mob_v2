package com.sza.fastmediasorter.core.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The reading rules of contract DEVICE-EXCHANGE 22 item CB on Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class ExchangeClipboardReaderTest {

    private val reader = ExchangeClipboardReader(RuntimeEnvironment.getApplication())

    @Test
    fun `a text item is text with its line ends made LF`() {
        val clip = ClipData.newPlainText("label", "one\r\ntwo\rthree\n")

        assertEquals(ExchangeClipboardSource.Text("one\ntwo\nthree\n"), reader.read(clip))
    }

    @Test
    fun `an HTML item is text`() {
        val clip = ClipData.newHtmlText("label", "bold", "<b>bold</b>")

        assertEquals(ExchangeClipboardSource.Text("bold"), reader.read(clip))
    }

    @Test
    fun `an image URI is an image`() {
        val source = reader.read(uriClip("image/png")) as ExchangeClipboardSource.Content

        assertEquals(ExchangeClipboardKind.IMAGE, source.kind)
        assertEquals("image/png", source.mimeType)
    }

    @Test
    fun `any other URI is a file`() {
        val source = reader.read(uriClip("application/pdf")) as ExchangeClipboardSource.Content

        assertEquals(ExchangeClipboardKind.FILE, source.kind)
    }

    @Test
    fun `a URI of no known type is a file of unknown type`() {
        val source = reader.read(uriClip(ClipDescription.MIMETYPE_TEXT_URILIST)) as ExchangeClipboardSource.Content

        assertEquals(ExchangeClipboardReader.MIME_UNKNOWN, source.mimeType)
    }

    @Test
    fun `two items are several objects`() {
        val clip = ClipData.newPlainText("label", "one").apply { addItem(ClipData.Item("two")) }

        assertEquals(ExchangeClipboardSource.Several, reader.read(clip))
    }

    @Test
    fun `no clip and an empty text are empty`() {
        assertEquals(ExchangeClipboardSource.Empty, reader.read(null))
        assertEquals(ExchangeClipboardSource.Empty, reader.read(ClipData.newPlainText("label", "")))
    }

    @Test
    fun `an intent item cannot be sent`() {
        val clip = ClipData.newIntent("label", Intent(Intent.ACTION_VIEW))

        assertEquals(ExchangeClipboardSource.Unsupported, reader.read(clip))
    }

    private fun uriClip(mimeType: String) =
        ClipData(ClipDescription("label", arrayOf(mimeType)), ClipData.Item(Uri.parse("content://test.provider/1")))
}
