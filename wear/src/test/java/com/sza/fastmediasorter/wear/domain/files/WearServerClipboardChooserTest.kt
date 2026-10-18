package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** S4227: contract DEVICE-EXCHANGE 22 item CA - the latest value wins, the first entry breaks a tie. */
class WearServerClipboardChooserTest {

    @Test
    fun `the latest storedAt wins`() {
        val answers = listOf(value("2026-10-14T10:00:00Z"), value("2026-10-14T11:00:00Z"), null)

        assertEquals(1, WearServerClipboardChooser.choose(answers))
    }

    @Test
    fun `equal times go to the entry listed first`() {
        val answers = listOf(null, value("2026-10-14T10:00:00Z"), value("2026-10-14T10:00:00Z"))

        assertEquals(1, WearServerClipboardChooser.choose(answers))
    }

    @Test
    fun `all empty gives nothing`() {
        assertNull(WearServerClipboardChooser.choose(listOf(null, null)))
    }

    @Test
    fun `an unreadable time ranks below any readable one`() {
        val answers = listOf(value("not a time"), value("2020-01-01T00:00:00Z"))

        assertEquals(1, WearServerClipboardChooser.choose(answers))
    }

    @Test
    fun `a time past the millisecond range ranks below any readable one`() {
        val answers = listOf(value(FAR_FUTURE), value("2020-01-01T00:00:00Z"))

        assertEquals(1, WearServerClipboardChooser.choose(answers))
    }

    @Test
    fun `a time before the millisecond range ranks below any readable one`() {
        val answers = listOf(value("2020-01-01T00:00:00Z"), value(FAR_PAST), value("2021-01-01T00:00:00Z"))

        assertEquals(2, WearServerClipboardChooser.choose(answers))
    }

    @Test
    fun `an out of range time is still a value when no answer is readable`() {
        val answers = listOf(null, value(FAR_FUTURE), value("not a time"))

        assertEquals(1, WearServerClipboardChooser.choose(answers))
    }

    @Test
    fun `storedAtMillis reads an ordinary time`() {
        assertEquals(1_000L, value("1970-01-01T00:00:01Z").storedAtMillis)
    }

    @Test
    fun `storedAtMillis is null for a text that is not a time`() {
        assertNull(value("not a time").storedAtMillis)
    }

    @Test
    fun `storedAtMillis is null for a time outside the millisecond range`() {
        assertNull(value(FAR_FUTURE).storedAtMillis)
        assertNull(value(FAR_PAST).storedAtMillis)
    }

    @Test
    fun `storedAtMillis keeps the two instants at the edge of the range`() {
        assertEquals(Long.MAX_VALUE, value(Instant.ofEpochMilli(Long.MAX_VALUE).toString()).storedAtMillis)
        assertEquals(Long.MIN_VALUE, value(Instant.ofEpochMilli(Long.MIN_VALUE).toString()).storedAtMillis)
    }

    private fun value(storedAt: String) = WearServerClipboardValue(
        clipId = "id",
        kind = WearServerClipboardKind.TEXT,
        size = 1,
        sha256 = "00",
        storedAt = storedAt,
        sourceDeviceId = null,
        sourceDeviceName = null,
        mimeType = null,
        name = null,
    )

    private companion object {
        // Instant.MAX and Instant.MIN: both parse, neither fits in epoch milliseconds.
        const val FAR_FUTURE = "+1000000000-12-31T23:59:59.999999999Z"
        const val FAR_PAST = "-1000000000-01-01T00:00:00Z"
    }
}
