package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S3802: the receiver is what a Local Folder pick completes against after process death, so each entry
 * must write exactly its own setting and read it back.
 */
class LocalFolderReceiverTest {

    @Test
    fun `every receiver reads back the id it wrote`() {
        LocalFolderReceiver.entries.forEach { receiver ->
            val written = receiver.write(AppSettings(), RESOURCE_ID)
            assertEquals(receiver.name, RESOURCE_ID, receiver.read(written))
        }
    }

    @Test
    fun `writing null clears the receiver`() {
        LocalFolderReceiver.entries.forEach { receiver ->
            val cleared = receiver.write(receiver.write(AppSettings(), RESOURCE_ID), null)
            assertNull(receiver.name, receiver.read(cleared))
        }
    }

    @Test
    fun `a write leaves every other receiver untouched`() {
        LocalFolderReceiver.entries.forEach { receiver ->
            val written = receiver.write(AppSettings(), RESOURCE_ID)
            LocalFolderReceiver.entries.filter { it != receiver }.forEach { other ->
                assertNull("${receiver.name} leaked into ${other.name}", other.read(written))
            }
        }
    }

    private companion object {
        const val RESOURCE_ID = 42L
    }
}
