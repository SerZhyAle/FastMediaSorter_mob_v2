package com.sza.fastmediasorter.wear.ui.phone

import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1730: a new watch paired with an older phone receives a page with no thumbnail field at all.
 * Every item must settle on the definite absence, because the not-yet state would leave each cell
 * waiting for bytes that are never coming instead of showing its type icon.
 */
class PhoneResourceThumbnailTest {

    @Test
    fun `page from an older phone maps every item to the definite absence`() {
        val page = listOf(
            item(token = "t1", name = "DCIM", isDirectory = true),
            item(token = "t2", name = "photo.jpg"),
            item(token = "t3", name = "clip.mp4")
        )

        val mapped = page.map { it.toWearThumbnail() }

        assertEquals(page.size, mapped.size)
        assertTrue(
            "An absent field must never read as Loading, was $mapped",
            mapped.all { it == WearThumbnail.Unavailable }
        )
    }

    private fun item(
        token: String,
        name: String,
        isDirectory: Boolean = false
    ): WearPhoneResourceItem = WearPhoneResourceItem(
        token = token,
        name = name,
        isDirectory = isDirectory
    )
}
