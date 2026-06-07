package com.sza.fastmediasorter.data.capture

import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Routing tests for [CameraCaptureSaver] (S0369): the three save destinations and the temp-file
 * cleanup contract. Robolectric supplies a real [android.content.Context] and a writable external
 * storage root so local and DCIM copies can be asserted on disk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraCaptureSaverTest {

    private val context = RuntimeEnvironment.getApplication()
    private val saver = CameraCaptureSaver(context)

    private fun newTempFile(content: String = "jpeg-bytes"): File =
        File.createTempFile("cap_test_", ".jpg").apply { writeText(content) }

    @Test
    fun `local resource saves under resource root and deletes temp`() = runTest {
        val temp = newTempFile()
        val root = File(context.cacheDir, "local_root_${System.nanoTime()}").apply { mkdirs() }
        val target = CameraCaptureTarget.Resource(
            id = 1L, name = "Local", path = root.absolutePath, type = ResourceType.LOCAL,
        )

        val result = saver.save(temp, "photo.jpg", target) { _, _, _ -> error("upload must not run for LOCAL") }

        assertTrue(result is SaveResult.Success)
        val saved = File(root, "photo.jpg")
        assertTrue("expected file at ${saved.absolutePath}", saved.exists())
        assertEquals(saved.absolutePath, (result as SaveResult.Success).savedPath)
        assertFalse("temp file must be deleted", temp.exists())
    }

    @Test
    fun `camera-folder pseudo-target routes to DCIM Camera`() = runTest {
        val temp = newTempFile()

        val result = saver.save(temp, "shot.jpg", CameraCaptureTarget.CameraFolder) { _, _, _ ->
            error("upload must not run for camera folder")
        }

        assertTrue(result is SaveResult.Success)
        val savedPath = (result as SaveResult.Success).savedPath
        assertTrue("expected DCIM/Camera path, got $savedPath", savedPath.replace('\\', '/').endsWith("/DCIM/Camera/shot.jpg"))
        assertTrue("expected saved file on disk", File(savedPath).exists())
        assertFalse(temp.exists())
    }

    @Test
    fun `virtual resource path also routes to DCIM Camera without upload`() = runTest {
        val temp = newTempFile()
        val target = CameraCaptureTarget.Resource(
            id = 2L, name = "Camera Photos", path = "virtual://camera_photos", type = ResourceType.LOCAL,
        )

        val result = saver.save(temp, "v.jpg", target) { _, _, _ -> error("upload must not run for virtual path") }

        assertTrue(result is SaveResult.Success)
        assertTrue((result as SaveResult.Success).savedPath.replace('\\', '/').endsWith("/DCIM/Camera/v.jpg"))
    }

    @Test
    fun `network resource delegates to upload strategy`() = runTest {
        val temp = newTempFile()
        var uploadedName: String? = null
        var uploadedResource: CameraCaptureTarget.Resource? = null
        val target = CameraCaptureTarget.Resource(
            id = 3L, name = "NAS", path = "smb://host/share", type = ResourceType.SMB,
        )

        val result = saver.save(temp, "net.jpg", target) { _, name, res ->
            uploadedName = name
            uploadedResource = res
            true
        }

        assertTrue(result is SaveResult.Success)
        assertEquals("net.jpg", uploadedName)
        assertEquals(target, uploadedResource)
        // Saved path for a network target is the remote root joined with the file name.
        assertEquals("smb://host/share/net.jpg", (result as SaveResult.Success).savedPath)
        assertFalse(temp.exists())
    }

    @Test
    fun `failed upload yields Generic failure and still deletes temp`() = runTest {
        val temp = newTempFile()
        val target = CameraCaptureTarget.Resource(
            id = 4L, name = "FTP", path = "ftp://host/dir", type = ResourceType.FTP,
        )

        val result = saver.save(temp, "fail.jpg", target) { _, _, _ -> false }

        assertEquals(SaveResult.Failure.Generic, result)
        assertFalse(temp.exists())
    }

    @Test
    fun `upload throwing IOException maps to Io failure`() = runTest {
        val temp = newTempFile()
        val target = CameraCaptureTarget.Resource(
            id = 5L, name = "SFTP", path = "sftp://host/dir", type = ResourceType.SFTP,
        )

        val result = saver.save(temp, "io.jpg", target) { _, _, _ -> throw java.io.IOException("boom") }

        assertEquals(SaveResult.Failure.Io, result)
        assertFalse(temp.exists())
    }
}
