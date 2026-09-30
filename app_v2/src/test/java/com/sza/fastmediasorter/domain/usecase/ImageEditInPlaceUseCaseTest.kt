package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * S3967: rotate, adjust and filter rewrite the user's only copy of a photo, judged like
 * FlipImageUseCaseTest (S3938) - the new pixels after a success, the untouched bytes after a failure,
 * and no temporary sibling left behind in either case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEditInPlaceUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val rotate = RotateImageUseCase(context, mockk(relaxed = true))
    private val adjust = AdjustImageUseCase(context, mockk(relaxed = true))
    private val filter = ApplyImageFilterUseCase(context, mockk(relaxed = true))

    @Test
    fun `a half turn swaps the left and right pixels in place`() = runTest {
        val image = twoPixelPng(left = Color.RED, right = Color.BLUE)

        val result = rotate.execute(image.path, HALF_TURN, recordStats = false)

        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val rotated = BitmapFactory.decodeFile(image.path)
        assertEquals(Color.BLUE, rotated.getPixel(0, 0))
        assertEquals(Color.RED, rotated.getPixel(1, 0))
        assertOnlyFile(image)
    }

    @Test
    fun `a negative filter inverts the pixels in place`() = runTest {
        val image = twoPixelPng(left = Color.BLACK, right = Color.WHITE)

        val result = filter.execute(image.path, ApplyImageFilterUseCase.FilterType.NEGATIVE, recordStats = false)

        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val inverted = BitmapFactory.decodeFile(image.path)
        assertEquals(Color.WHITE, inverted.getPixel(0, 0))
        assertEquals(Color.BLACK, inverted.getPixel(1, 0))
        assertOnlyFile(image)
    }

    @Test
    fun `full desaturation greys the pixels in place`() = runTest {
        val image = twoPixelPng(left = Color.RED, right = Color.RED)

        val result = adjust.execute(image.path, AdjustImageUseCase.Adjustments(saturation = 0f), recordStats = false)

        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val grey = BitmapFactory.decodeFile(image.path).getPixel(0, 0)
        assertEquals(Color.red(grey), Color.green(grey))
        assertEquals(Color.green(grey), Color.blue(grey))
        assertOnlyFile(image)
    }

    @Test
    fun `an undecodable file fails every edit and keeps its original bytes`() = runTest {
        val image = File(tempFolder.newFolder("broken"), "broken.jpg")
        val original = "this is not a picture".toByteArray()
        image.writeBytes(original)

        val results = listOf(
            rotate.execute(image.path, HALF_TURN, recordStats = false),
            adjust.execute(image.path, AdjustImageUseCase.Adjustments(brightness = 10f), recordStats = false),
            filter.execute(image.path, ApplyImageFilterUseCase.FilterType.SEPIA, recordStats = false),
        )

        results.forEach { assertTrue(it.isFailure) }
        assertArrayEquals(original, image.readBytes())
        assertOnlyFile(image)
    }

    private fun assertOnlyFile(image: File) {
        assertEquals(listOf(image.name), image.parentFile!!.list()!!.toList())
    }

    private fun twoPixelPng(left: Int, right: Int): File {
        val file = File(tempFolder.newFolder("images"), "pair.png")
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, left)
        bitmap.setPixel(1, 0, right)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private companion object {
        const val HALF_TURN = 180f
    }
}
