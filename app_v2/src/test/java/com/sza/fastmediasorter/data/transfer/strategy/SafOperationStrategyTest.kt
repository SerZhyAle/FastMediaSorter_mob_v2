package com.sza.fastmediasorter.data.transfer.strategy

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S1378: routing and single-file behaviour of [SafOperationStrategy].
 *
 * The routing assertions are the load-bearing ones: before this strategy existed, a `content://`
 * path fell through to [LocalOperationStrategy], whose own `supportsProtocol` excludes `content:/` -
 * so the dispatcher handed the local strategy work it declares it cannot do. If this class ever goes
 * red on `supportsProtocol`, that silent misrouting is back.
 *
 * Robolectric because `SafHelper` parses addresses through `android.net.Uri`, which is not available
 * to a plain JVM test; the `ContentResolver` itself is mocked, so nothing touches a real provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class SafOperationStrategyTest {

    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true) {
        every { this@mockk.contentResolver } returns this@SafOperationStrategyTest.contentResolver
    }
    private val strategy = SafOperationStrategy(context)

    @Test
    fun `supportsProtocol accepts a document-tree address`() {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADCIM"

        assertTrue(strategy.supportsProtocol(treeUri))
        assertTrue(
            "the single-slash form reaches us from stored paths too",
            strategy.supportsProtocol("content:/tree/x")
        )
    }

    @Test
    fun `supportsProtocol rejects every other address shape`() {
        assertFalse(strategy.supportsProtocol("/storage/emulated/0/DCIM/photo.jpg"))
        assertFalse(strategy.supportsProtocol("smb://server/share/photo.jpg"))
        assertFalse(strategy.supportsProtocol("cloud://drive/folder/photo.jpg"))
        assertFalse(strategy.supportsProtocol("sftp://host/home/photo.jpg"))
        assertFalse(strategy.supportsProtocol("ftp://host/pub/photo.jpg"))
    }

    @Test
    fun `getProtocolName is the key the dispatcher routes on`() {
        assertEquals("saf", strategy.getProtocolName())
    }

    @Test
    fun `copy of a source the provider cannot open fails instead of reporting success`() = runTest {
        every { contentResolver.openInputStream(any<Uri>()) } returns null
        val destination = File.createTempFile("saf-copy", ".bin").also { it.deleteOnExit() }

        val result = strategy.copyFile(
            source = "content://com.android.externalstorage.documents/document/primary%3Amissing.jpg",
            destination = destination.absolutePath,
            overwrite = true,
            progressCallback = null
        )

        assertTrue("an unreadable source must not report success", result.isFailure)
    }

    @Test
    fun `delete refuses a path that is not a document-tree address`() = runTest {
        val result = strategy.deleteFile("/storage/emulated/0/DCIM/photo.jpg")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    /**
     * A misroute must be loud. Answering `false` for a local path that exists is how a dispatcher bug
     * hides: the caller believes the file is gone and acts on it.
     */
    @Test
    fun `exists refuses a path outside the addressing domain rather than answering false`() = runTest {
        val realFile = File.createTempFile("saf-exists", ".bin").also { it.deleteOnExit() }

        val result = strategy.exists(realFile.absolutePath)

        assertTrue("a local path is not this strategy's to answer for", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
