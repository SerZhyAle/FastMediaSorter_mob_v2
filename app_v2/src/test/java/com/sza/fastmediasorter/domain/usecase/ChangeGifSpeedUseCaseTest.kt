package com.sza.fastmediasorter.domain.usecase

import android.graphics.Bitmap
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.sza.fastmediasorter.util.gif.AnimatedGifEncoder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * [ChangeGifSpeedUseCase.reencodeFrames] streams each decoded frame straight into the encoder; the
 * re-encoded GIF must keep every frame and carry the delays divided by the speed factor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class ChangeGifSpeedUseCaseTest {

    private val useCase = ChangeGifSpeedUseCase(mockk(relaxed = true))

    @Test
    fun `re-encoding keeps every frame and divides the delays by the speed`() {
        val source = decoderFor(sourceGif())
        val out = ByteArrayOutputStream()

        val written = useCase.reencodeFrames(source, speed = 2f, out = out)

        assertEquals(FRAME_COUNT, written)
        val result = decoderFor(out.toByteArray())
        assertEquals(FRAME_COUNT, result.frameCount)
        for (i in 0 until FRAME_COUNT) assertEquals(SOURCE_DELAY_MS / 2, result.getDelay(i))
    }

    @Test
    fun `a decoder that yields no frame writes nothing`() {
        val empty = mockk<GifDecoder>(relaxed = true)
        every { empty.frameCount } returns FRAME_COUNT
        every { empty.nextFrame } returns null
        val out = ByteArrayOutputStream()

        val written = useCase.reencodeFrames(empty, speed = 1f, out = out)

        assertEquals(0, written)
        assertEquals(0, out.size())
    }

    private fun sourceGif(): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        encoder.start(out)
        encoder.setRepeat(0)
        encoder.setDelay(SOURCE_DELAY_MS)
        repeat(FRAME_COUNT) { encoder.addFrame(solidFrame(COLOURS[it])) }
        encoder.finish()
        return out.toByteArray()
    }

    private fun solidFrame(colour: Int): Bitmap =
        Bitmap.createBitmap(IntArray(SIZE * SIZE) { colour }, SIZE, SIZE, Bitmap.Config.ARGB_8888)

    private fun decoderFor(bytes: ByteArray): GifDecoder =
        StandardGifDecoder(PlainBitmapProvider()).apply { read(bytes) }

    private class PlainBitmapProvider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)

        override fun release(bitmap: Bitmap) = Unit

        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)

        override fun release(bytes: ByteArray) = Unit

        override fun obtainIntArray(size: Int): IntArray = IntArray(size)

        override fun release(array: IntArray) = Unit
    }

    private companion object {
        const val SIZE = 8
        const val FRAME_COUNT = 3
        const val SOURCE_DELAY_MS = 100
        val COLOURS = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
    }
}
