package com.sza.fastmediasorter.util.gif

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * S3846: the nearest-colour memo and the per-frame buffer reuse must not change a single output byte.
 * The digest was recorded against the encoder before either change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class AnimatedGifEncoderTest {

    @Test
    fun `frames with more than 256 colours encode to the pinned bytes`() {
        val out = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder()
        encoder.start(out)
        encoder.setRepeat(0)
        encoder.setDelay(FRAME_DELAY_MS)
        assertTrue(encoder.addFrame(gradientFrame(seed = 0)))
        assertTrue(encoder.addFrame(gradientFrame(seed = SECOND_FRAME_SEED)))
        assertTrue(encoder.finish())

        val bytes = out.toByteArray()
        assertEquals("GIF89a", String(bytes, 0, GIF_HEADER_LENGTH, Charsets.US_ASCII))
        assertEquals(GIF_TRAILER, bytes.last().toInt() and BYTE_MASK)
        assertEquals(PINNED_SHA256, sha256(bytes))
    }

    // Each channel is spread so the frame carries far more than 256 distinct colours, and colours
    // repeat across rows so the out-of-palette path meets the same colour more than once.
    private fun gradientFrame(seed: Int): Bitmap {
        val pixels = IntArray(SIZE * SIZE) { i ->
            val x = i % SIZE
            val y = i / SIZE
            val r = (x * CHANNEL_STEP + seed) and BYTE_MASK
            val g = (y * CHANNEL_STEP) and BYTE_MASK
            val b = ((x + y) * CHANNEL_STEP / 2) and BYTE_MASK
            OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SIZE = 40
        const val CHANNEL_STEP = 7
        const val SECOND_FRAME_SEED = 90
        const val FRAME_DELAY_MS = 120
        const val GIF_HEADER_LENGTH = 6
        const val GIF_TRAILER = 0x3b
        const val BYTE_MASK = 0xFF
        const val OPAQUE = -0x1000000
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val PINNED_SHA256 = "59752316a8ce2ec6c24cadfc9f0f2c438db320c1101bc5adb9d8417e9250b2b7"
    }
}
