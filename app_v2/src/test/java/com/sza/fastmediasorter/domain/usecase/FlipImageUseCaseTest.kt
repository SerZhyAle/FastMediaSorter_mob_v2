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
 * S3938: the flip rewrites the user's only copy of a photo, so the file on disk is what is judged -
 * the new pixels after a success, the untouched bytes after a failure, and no temporary sibling left
 * behind in either case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlipImageUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val useCase = FlipImageUseCase(RuntimeEnvironment.getApplication(), mockk(relaxed = true))

    @Test
    fun `a horizontal flip swaps the left and right pixels in place`() = runTest {
        val image = twoPixelPng("pair.png", left = Color.RED, right = Color.BLUE)

        val result = useCase.execute(image.path, FlipImageUseCase.FlipDirection.HORIZONTAL, recordStats = false)

        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val flipped = BitmapFactory.decodeFile(image.path)
        assertEquals(Color.BLUE, flipped.getPixel(0, 0))
        assertEquals(Color.RED, flipped.getPixel(1, 0))
        assertEquals(listOf("pair.png"), image.parentFile!!.list()!!.toList())
    }

    @Test
    fun `an undecodable file fails and keeps its original bytes`() = runTest {
        val image = File(tempFolder.newFolder("broken"), "broken.jpg")
        val original = "this is not a picture".toByteArray()
        image.writeBytes(original)

        val result = useCase.execute(image.path, FlipImageUseCase.FlipDirection.VERTICAL, recordStats = false)

        assertTrue(result.isFailure)
        assertArrayEquals(original, image.readBytes())
        assertEquals(listOf("broken.jpg"), image.parentFile!!.list()!!.toList())
    }

    private fun twoPixelPng(name: String, left: Int, right: Int): File {
        val file = File(tempFolder.newFolder("images"), name)
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, left)
        bitmap.setPixel(1, 0, right)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}
