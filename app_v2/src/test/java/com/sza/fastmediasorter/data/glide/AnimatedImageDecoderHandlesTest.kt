package com.sza.fastmediasorter.data.glide

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.resource.gif.GifOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/**
 * S1026: the animated WebP/APNG decoders must decline a request that asked for a still. Glide's
 * DrawableToBitmapConverter refuses Animatable drawables outright, so a thumbnail request (which
 * always calls `dontAnimate()`) fails its whole load rather than degrading to a first frame unless
 * the decoder steps aside and lets the built-in downsampler decode that frame.
 */
class AnimatedImageDecoderHandlesTest {

    private fun animatedWebpHeader(): ByteArray {
        // RIFF????WEBPVP8X + flags byte with the animation bit set, padded past the sniffer minimum.
        val header = ByteArray(64)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        "WEBP".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
        "VP8X".toByteArray(Charsets.US_ASCII).copyInto(header, 12)
        header[20] = 0x02
        return header
    }

    private fun staticWebpHeader(): ByteArray = animatedWebpHeader().also { it[20] = 0x00 }

    private fun optionsWithAnimationDisabled(disabled: Boolean) = Options().apply {
        set(GifOptions.DISABLE_ANIMATION, disabled)
    }

    @Test
    fun `byte buffer decoder claims an animated webp by default`() {
        val decoder = AnimatedImageByteBufferDecoder()
        assertTrue(decoder.handles(ByteBuffer.wrap(animatedWebpHeader()), Options()))
    }

    @Test
    fun `byte buffer decoder declines when the request disabled animation`() {
        val decoder = AnimatedImageByteBufferDecoder()
        val options = optionsWithAnimationDisabled(true)
        assertFalse(decoder.handles(ByteBuffer.wrap(animatedWebpHeader()), options))
    }

    @Test
    fun `byte buffer decoder still claims an animated webp when animation is explicitly enabled`() {
        val decoder = AnimatedImageByteBufferDecoder()
        val options = optionsWithAnimationDisabled(false)
        assertTrue(decoder.handles(ByteBuffer.wrap(animatedWebpHeader()), options))
    }

    @Test
    fun `stream decoder declines when the request disabled animation`() {
        val decoder = AnimatedImageStreamDecoder()
        val options = optionsWithAnimationDisabled(true)
        assertFalse(decoder.handles(ByteArrayInputStream(animatedWebpHeader()), options))
    }

    @Test
    fun `stream decoder claims an animated webp by default`() {
        val decoder = AnimatedImageStreamDecoder()
        assertTrue(decoder.handles(ByteArrayInputStream(animatedWebpHeader()), Options()))
    }

    @Test
    fun `a static webp is never claimed`() {
        assertFalse(AnimatedImageByteBufferDecoder().handles(ByteBuffer.wrap(staticWebpHeader()), Options()))
        assertFalse(AnimatedImageStreamDecoder().handles(ByteArrayInputStream(staticWebpHeader()), Options()))
    }
}
