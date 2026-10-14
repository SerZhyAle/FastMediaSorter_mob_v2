package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
