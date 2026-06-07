package com.sza.fastmediasorter.util

import android.os.Environment
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S0367 destination-resolution contract. Robolectric so [Environment] public-directory
 * lookups (the fallback paths) resolve against a sandboxed filesystem.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
class CaptureDestinationPolicyTest {

    @Test
    fun `isUsableTarget rejects null`() {
        assertFalse(CaptureDestinationPolicy.isUsableTarget(null))
    }

    @Test
    fun `isUsableTarget rejects read-only resource`() {
        val resource = writableFolder(path = newRealDir().absolutePath, isReadOnly = true)

        assertFalse(CaptureDestinationPolicy.isUsableTarget(resource))
    }

    @Test
    fun `isUsableTarget rejects virtual resource`() {
        val resource = writableFolder(path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS)

        assertFalse(CaptureDestinationPolicy.isUsableTarget(resource))
    }

    @Test
    fun `isUsableTarget accepts writable non-virtual folder`() {
        val resource = writableFolder(path = newRealDir().absolutePath)

        assertTrue(CaptureDestinationPolicy.isUsableTarget(resource))
    }

    @Test
    fun `resolveMicDestination returns selected writable folder`() {
        val dir = newRealDir()
        val resource = writableFolder(path = dir.absolutePath)

        assertEquals(dir.absolutePath, CaptureDestinationPolicy.resolveMicDestination(resource).absolutePath)
    }

    @Test
    fun `resolveMicDestination falls back to Downloads when selection is null`() {
        val expected = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        assertEquals(expected.absolutePath, CaptureDestinationPolicy.resolveMicDestination(null).absolutePath)
    }

    @Test
    fun `resolveMicDestination falls back to Downloads when selection is read-only`() {
        val expected = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val resource = writableFolder(path = newRealDir().absolutePath, isReadOnly = true)

        assertEquals(expected.absolutePath, CaptureDestinationPolicy.resolveMicDestination(resource).absolutePath)
    }

    @Test
    fun `resolveCameraDestination returns selected writable folder`() {
        val dir = newRealDir()
        val resource = writableFolder(path = dir.absolutePath)

        assertEquals(dir.absolutePath, CaptureDestinationPolicy.resolveCameraDestination(resource).absolutePath)
    }

    @Test
    fun `resolveCameraDestination falls back to device camera folder when selection is null`() {
        val expected = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Camera"
        )

        assertEquals(expected.absolutePath, CaptureDestinationPolicy.resolveCameraDestination(null).absolutePath)
    }

    @Test
    fun `resolveCameraDestination falls back to device camera folder when selection is virtual`() {
        val expected = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Camera"
        )
        val resource = writableFolder(path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES)

        assertEquals(expected.absolutePath, CaptureDestinationPolicy.resolveCameraDestination(resource).absolutePath)
    }

    /** Creates a real, writable directory on the test filesystem. */
    private fun newRealDir(): File =
        File.createTempFile("capture_dest", "").let { tmp ->
            tmp.delete()
            tmp.mkdirs()
            tmp
        }

    private fun writableFolder(
        path: String,
        isReadOnly: Boolean = false
    ): MediaResource = MediaResource(
        id = 1L,
        name = "Target",
        path = path,
        type = ResourceType.LOCAL,
        supportedMediaTypes = setOf(MediaType.IMAGE),
        isWritable = true,
        isReadOnly = isReadOnly
    )
}
