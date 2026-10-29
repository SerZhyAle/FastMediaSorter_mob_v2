package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4248: the scheduled-message wire format; the watch module carries the same test over its mirror. */
class WearScheduledMessageCodecTest {

    private val gson = Gson()

    @Test
    fun `message with a picture round-trips`() {
        val sent = WearScheduledMessagePayload(
            messageId = "7-1000",
            title = "Water",
            text = "Drink a glass",
            imageJpegBase64 = "AAEC/w==",
        )

        val parsed = WearScheduledMessageCodec.parse(requireNotNull(WearScheduledMessageCodec.encode(sent, gson)), gson)

        assertEquals(WearScheduledMessageParseResult.Parsed(sent), parsed)
    }

    @Test
    fun `text-only message round-trips with null picture`() {
        val sent = WearScheduledMessagePayload(messageId = "7-1001", title = "Stand up")

        val parsed = WearScheduledMessageCodec.parse(requireNotNull(WearScheduledMessageCodec.encode(sent, gson)), gson)

        assertTrue(parsed is WearScheduledMessageParseResult.Parsed)
        assertNull((parsed as WearScheduledMessageParseResult.Parsed).payload.imageJpegBase64)
    }

    @Test
    fun `oversized message is refused on both ends`() {
        val huge = "A".repeat(WearScheduledMessagePayload.MAX_MESSAGE_BYTES)
        val sent = WearScheduledMessagePayload(messageId = "7-1002", title = "Big", imageJpegBase64 = huge)

        assertNull(WearScheduledMessageCodec.encode(sent, gson))
        assertEquals(
            WearScheduledMessageParseResult.TooLarge,
            WearScheduledMessageCodec.parse(ByteArray(WearScheduledMessagePayload.MAX_MESSAGE_BYTES + 1), gson),
        )
    }

    @Test
    fun `unknown format version is reported with its id, garbage is malformed`() {
        val future = """{"messageId":"m","formatVersion":9,"title":"t"}""".toByteArray()

        assertEquals(
            WearScheduledMessageParseResult.UnsupportedVersion(9, "m"),
            WearScheduledMessageCodec.parse(future, gson),
        )
        assertEquals(
            WearScheduledMessageParseResult.Malformed(),
            WearScheduledMessageCodec.parse("{]".toByteArray(), gson),
        )
    }

    @Test
    fun `a malformed message keeps the id it carried`() {
        assertEquals(
            WearScheduledMessageParseResult.Malformed("m"),
            WearScheduledMessageCodec.parse("""{"messageId":"m"}""".toByteArray(), gson),
        )
        assertEquals(
            WearScheduledMessageParseResult.Malformed(),
            WearScheduledMessageCodec.parse("""{"title":"t"}""".toByteArray(), gson),
        )
    }

    @Test
    fun `receipt round-trips and a receipt without id is dropped`() {
        val receipt = WearScheduledMessageReceipt(
            messageId = "7-1000",
            shown = false,
            reason = WearScheduledMessageReceiptReasons.NO_NOTIFICATION_PERMISSION,
        )

        val encoded = WearScheduledMessageCodec.encodeReceipt(receipt, gson)

        assertEquals(receipt, WearScheduledMessageCodec.parseReceipt(encoded, gson))
        assertNull(WearScheduledMessageCodec.parseReceipt("""{"shown":true}""".toByteArray(), gson))
    }
}
