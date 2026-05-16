package com.sza.fastmediasorter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtensionThumbnailGeneratorTest {

    @Test
    fun `same extension reuses cached bitmap instance`() {
        ExtensionThumbnailGenerator.clearCache()

        val first = ExtensionThumbnailGenerator.generate("mp3")
        val second = ExtensionThumbnailGenerator.generate("MP3")

        assertSame(first, second)
        assertEquals(96, first.width)
        assertEquals(96, first.height)
    }
}