package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2476: tests extension stripping logic on WearPhoneResourceItem for watch display.
 */
class WearPhoneResourceItemTest {

    @Test
    fun `file entry strips extension`() {
        val item = WearPhoneResourceItem(
            token = "1",
            name = "photo.jpg",
            isDirectory = false
        )
        assertEquals("photo", item.displayName)
    }

    @Test
    fun `file entry with multiple dots strips last extension`() {
        val item = WearPhoneResourceItem(
            token = "2",
            name = "my.archive.tar.gz",
            isDirectory = false
        )
        assertEquals("my.archive.tar", item.displayName)
    }

    @Test
    fun `file entry without extension keeps full name`() {
        val item = WearPhoneResourceItem(
            token = "3",
            name = "README",
            isDirectory = false
        )
        assertEquals("README", item.displayName)
    }

    @Test
    fun `directory entry with dot keeps full name`() {
        val item = WearPhoneResourceItem(
            token = "4",
            name = "v1.0.photos",
            isDirectory = true
        )
        assertEquals("v1.0.photos", item.displayName)
    }
}
