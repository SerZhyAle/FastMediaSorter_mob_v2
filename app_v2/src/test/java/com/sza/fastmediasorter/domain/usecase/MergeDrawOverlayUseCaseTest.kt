package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.OutputStream

/**
 * JVM coverage for [MergeDrawOverlayUseCase]. The android.jar stubs return defaults, so Canvas is inert and
 * the merged bitmap is a mock whose compress() outcome each case chooses.
 */
class MergeDrawOverlayUseCaseTest {

    private val useCase = MergeDrawOverlayUseCase()
    private val overlay = mockk<Bitmap>(relaxed = true)

    private fun baseReturning(merged: Bitmap): Bitmap = mockk<Bitmap>().also {
        every { it.copy(Bitmap.Config.ARGB_8888, true) } returns merged
    }

    @Test
    fun `successful encode returns the written bytes and recycles`() = runTest {
        val merged = mockk<Bitmap>(relaxed = true)
        every { merged.compress(any(), any(), any()) } answers {
            thirdArg<OutputStream>().write(byteArrayOf(1, 2, 3))
            true
        }

        val result = useCase.execute(baseReturning(merged), overlay, Bitmap.CompressFormat.JPEG)

        assertArrayEquals(byteArrayOf(1, 2, 3), result.getOrThrow())
        verify(exactly = 1) { merged.recycle() }
    }

    @Test
    fun `compress returning false fails the result and recycles`() = runTest {
        val merged = mockk<Bitmap>(relaxed = true)
        every { merged.compress(any(), any(), any()) } returns false

        val result = useCase.execute(baseReturning(merged), overlay, Bitmap.CompressFormat.JPEG)

        assertTrue(result.isFailure)
        verify(exactly = 1) { merged.recycle() }
    }

    @Test
    fun `empty encode fails the result`() = runTest {
        val merged = mockk<Bitmap>(relaxed = true)
        every { merged.compress(any(), any(), any()) } returns true

        val result = useCase.execute(baseReturning(merged), overlay, Bitmap.CompressFormat.PNG)

        assertTrue(result.isFailure)
    }

    @Test
    fun `compress throwing still recycles the merged bitmap`() = runTest {
        val merged = mockk<Bitmap>(relaxed = true)
        every { merged.compress(any(), any(), any()) } throws IllegalStateException("encoder")

        val result = useCase.execute(baseReturning(merged), overlay, Bitmap.CompressFormat.JPEG)

        assertTrue(result.isFailure)
        verify(exactly = 1) { merged.recycle() }
    }
}
