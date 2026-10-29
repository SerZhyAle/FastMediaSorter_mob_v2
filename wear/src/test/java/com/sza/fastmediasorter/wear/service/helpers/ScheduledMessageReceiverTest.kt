package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.core.notification.WearScheduledMessageNotifier
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessageCodec
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessagePayload
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessageReceiptReasons
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class ScheduledMessageReceiverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()
    private val context = mockk<Context>()
    private val capabilities = mockk<WearRestrictedCapabilities>()
    private val notifier = mockk<WearScheduledMessageNotifier>()
    private lateinit var receiver: ScheduledMessageReceiver

    @Before
    fun setUp() {
        every { context.cacheDir } returns tempFolder.root
        every { capabilities.offersContentTransfer } returns true
        every { notifier.notify(any(), any(), any()) } returns true
        receiver = ScheduledMessageReceiver(context, capabilities, notifier, gson)
    }

    @Test
    fun `refuses as not offered when the build carries no content transfer`() = runTest {
        every { capabilities.offersContentTransfer } returns false

        val receipt = receiver.receive(encode(WearScheduledMessagePayload("m1", title = "Hi")))

        assertFalse(receipt.shown)
        assertEquals("m1", receipt.messageId)
        assertEquals(WearScheduledMessageReceiptReasons.NOT_OFFERED, receipt.reason)
        verify(exactly = 0) { notifier.notify(any(), any(), any()) }
    }

    @Test
    fun `not offered wins over an unknown format version and keeps the id`() = runTest {
        every { capabilities.offersContentTransfer } returns false
        val bytes = """{"messageId":"m1","formatVersion":99,"title":"Hi"}""".toByteArray()

        val receipt = receiver.receive(bytes)

        assertEquals("m1", receipt.messageId)
        assertEquals(WearScheduledMessageReceiptReasons.NOT_OFFERED, receipt.reason)
    }

    @Test
    fun `refuses bytes with no readable id and answers with an empty one`() = runTest {
        val receipt = receiver.receive("not json".toByteArray())

        assertFalse(receipt.shown)
        assertEquals("", receipt.messageId)
        assertEquals(WearScheduledMessageReceiptReasons.MALFORMED, receipt.reason)
    }

    @Test
    fun `refuses a malformed message with the id it carried`() = runTest {
        val receipt = receiver.receive("""{"messageId":"m1"}""".toByteArray())

        assertFalse(receipt.shown)
        assertEquals("m1", receipt.messageId)
        assertEquals(WearScheduledMessageReceiptReasons.MALFORMED, receipt.reason)
    }

    @Test
    fun `refuses an unknown format version with its id`() = runTest {
        val bytes = """{"messageId":"m1","formatVersion":99,"title":"Hi"}""".toByteArray()

        val receipt = receiver.receive(bytes)

        assertFalse(receipt.shown)
        assertEquals("m1", receipt.messageId)
        assertEquals(WearScheduledMessageReceiptReasons.UNSUPPORTED_VERSION, receipt.reason)
    }

    @Test
    fun `shows a text-only message and answers shown`() = runTest {
        val receipt = receiver.receive(encode(WearScheduledMessagePayload("m1", title = "Hi", text = "There")))

        assertTrue(receipt.shown)
        assertEquals("m1", receipt.messageId)
        verify { notifier.notify("Hi", "There", null) }
    }

    @Test
    fun `answers no permission when the notifier cannot post`() = runTest {
        every { notifier.notify(any(), any(), any()) } returns false

        val receipt = receiver.receive(encode(WearScheduledMessagePayload("m1", title = "Hi")))

        assertFalse(receipt.shown)
        assertEquals("m1", receipt.messageId)
        assertEquals(WearScheduledMessageReceiptReasons.NO_NOTIFICATION_PERMISSION, receipt.reason)
    }

    @Test
    fun `caches the picture and hands it to the notifier`() = runTest {
        val picture = byteArrayOf(1, 2, 3, 4)
        val fileSlot = slot<File>()
        every { notifier.notify(any(), any(), capture(fileSlot)) } returns true
        val payload = WearScheduledMessagePayload(
            "m1",
            title = "Hi",
            imageJpegBase64 = Base64.getEncoder().encodeToString(picture),
        )

        val receipt = receiver.receive(encode(payload))

        assertTrue(receipt.shown)
        assertArrayEquals(picture, fileSlot.captured.readBytes())
        assertTrue(fileSlot.captured.path.startsWith(tempFolder.root.path))
    }

    @Test
    fun `an undecodable picture still shows the text`() = runTest {
        val payload = WearScheduledMessagePayload("m1", title = "Hi", imageJpegBase64 = "%%%")

        val receipt = receiver.receive(encode(payload))

        assertTrue(receipt.shown)
        assertNull(receipt.reason)
        verify { notifier.notify("Hi", null, null) }
    }

    private fun encode(payload: WearScheduledMessagePayload): ByteArray =
        requireNotNull(WearScheduledMessageCodec.encode(payload, gson))
}
