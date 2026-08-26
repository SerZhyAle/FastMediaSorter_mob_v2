package com.sza.fastmediasorter.domain.usecase

import android.graphics.BitmapFactory
import android.net.Uri
import com.sza.fastmediasorter.service.WearDataLayerPaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * S2000: the crop is the failure the watch cannot report - a wrongly cut frame simply shows up
 * stretched or clipped, with nothing to log - so strategic section 11.8 requires it proved here
 * rather than on a device.
 *
 * The expected edge is written as a literal rather than read from
 * [WearDataLayerPaths.BACKGROUND_IMAGE_EDGE_PX]: a test that restates the constant it checks would
 * keep passing if the constant were changed by accident, which is the one thing it exists to catch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrepareWearBackgroundImageUseCaseTest {

    private val app = RuntimeEnvironment.getApplication()
    private val useCase = PrepareWearBackgroundImageUseCase(app)

    @Test
    fun `a wide source is reduced to the canonical square`() = runTest {
        val frame = useCase(imageSource("wide", width = 1600, height = 900)).getOrThrow()

        assertEquals(480, edgesOf(frame).first)
        assertEquals(480, edgesOf(frame).second)
    }

    @Test
    fun `a tall source is reduced to the same canonical square`() = runTest {
        val frame = useCase(imageSource("tall", width = 900, height = 1600)).getOrThrow()

        assertEquals(480, edgesOf(frame).first)
        assertEquals(480, edgesOf(frame).second)
    }

    @Test
    fun `a source that cannot be decoded fails and leaves no frame behind`() = runTest {
        val target = File(app.cacheDir, WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME)
        target.delete()
        val source = Uri.parse("content://com.sza.fastmediasorter.test/not-an-image")
        shadowOf(app.contentResolver).registerInputStreamSupplier(source) {
            ByteArrayInputStream("this is not a picture".toByteArray())
        }

        val result = useCase(source)

        assertTrue(result.isFailure)
        assertFalse(target.exists())
    }

    /**
     * A supplier rather than a single stream: the use case reads the source twice, once for its
     * bounds and once for the pixels, and a stream handed out once comes back already exhausted.
     */
    private fun imageSource(name: String, width: Int, height: Int): Uri {
        val file = File(app.cacheDir, "$name.png")
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file)
        val source = Uri.parse("content://com.sza.fastmediasorter.test/$name")
        shadowOf(app.contentResolver).registerInputStreamSupplier(source) { file.inputStream() }
        return source
    }

    private fun edgesOf(frame: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(frame.path, bounds)
        return bounds.outWidth to bounds.outHeight
    }
}
