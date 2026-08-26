package com.sza.fastmediasorter.data.transfer

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S0266 - Verifies the cloud branch of [BaseFileOperationHandler.extractFileName] returns the
 * caller-supplied fallback (which is the display-name when the caller used [CloudFileHandle]).
 *
 * Robolectric: the content-URI branch calls Uri.decode, which returns null on the plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
class BaseFileOperationHandlerExtractFileNameTest {

    private val context: Context = mockk(relaxed = true)

    private val handler = object : BaseFileOperationHandler(context) {
        override fun getStrategies(): List<FileOperationStrategy> = emptyList()
        fun extractName(path: String, fallback: String): String = extractFileName(path, fallback)
    }

    @Test
    fun extractFileName_cloudPath_withDisplayName_returnsDisplayName() {
        val name = handler.extractName(
            path = "cloud://google_drive/17S0TkB5sDhJCSnJeHqFww0O08a8JxlKG",
            fallback = "MyVideo.mp4",
        )
        assertEquals("MyVideo.mp4", name)
    }

    @Test
    fun extractFileName_cloudPath_withBareFileId_returnsFileId() {
        // Caller did not pass display-name; extractFileName returns whatever fallback it got.
        // The defensive metadata fetch lives in CloudFileOperationHandler, not here.
        val name = handler.extractName(
            path = "cloud://google_drive/17S0TkB5sDhJCSnJeHqFww0O08a8JxlKG",
            fallback = "17S0TkB5sDhJCSnJeHqFww0O08a8JxlKG",
        )
        assertEquals("17S0TkB5sDhJCSnJeHqFww0O08a8JxlKG", name)
    }

    @Test
    fun extractFileName_contentUri_returnsLastSegment() {
        val name = handler.extractName(
            path = "content://media/external/images/123/MyPic.jpg",
            fallback = "fallback.jpg",
        )
        assertEquals("MyPic.jpg", name)
    }

    @Test
    fun extractFileName_localPath_returnsLastSegment() {
        val name = handler.extractName(
            path = "/storage/emulated/0/Download/MyVideo.mp4",
            fallback = "fallback.mp4",
        )
        assertEquals("MyVideo.mp4", name)
    }
}
