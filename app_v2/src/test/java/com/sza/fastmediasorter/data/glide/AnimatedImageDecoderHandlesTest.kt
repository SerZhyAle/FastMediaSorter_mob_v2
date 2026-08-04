package com.sza.fastmediasorter.data.glide

import android.content.res.Resources
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.resource.gif.GifOptions
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/**
 * S1317: the animated WebP/APNG decoders must still claim animated input even when the request
 * asked for a still frame. Declining would hand the file to Glide's built-in decoder, which ignores
 * the disable-animation option and still returns an AnimatedImageDrawable.
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

    // handles() never touches Resources - only decode() does - so a bare mock is enough here.
    private val resources: Resources = mockk()

    @Test
    fun `byte buffer decoder claims an animated webp by default`() {
        val decoder = AnimatedImageByteBufferDecoder(resources)
        assertTrue(decoder.handles(ByteBuffer.wrap(animatedWebpHeader()), Options()))
    }

    @Test
    fun `byte buffer decoder still claims an animated webp when animation is disabled`() {
        val decoder = AnimatedImageByteBufferDecoder(resources)
        val options = optionsWithAnimationDisabled(true)
        assertTrue(decoder.handles(ByteBuffer.wrap(animatedWebpHeader()), options))
    }

    @Test
    fun `byte buffer decoder still claims an animated webp when animation is explicitly enabled`() {
        val decoder = AnimatedImageByteBufferDecoder(resources)
        val options = optionsWithAnimationDisabled(false)
        assertTrue(decoder.handles(ByteBuffer.wrap(animatedWebpHeader()), options))
    }

    @Test
    fun `stream decoder still claims an animated webp when animation is disabled`() {
        val decoder = AnimatedImageStreamDecoder(resources)
        val options = optionsWithAnimationDisabled(true)
        assertTrue(decoder.handles(ByteArrayInputStream(animatedWebpHeader()), options))
    }

    @Test
    fun `stream decoder claims an animated webp by default`() {
        val decoder = AnimatedImageStreamDecoder(resources)
        assertTrue(decoder.handles(ByteArrayInputStream(animatedWebpHeader()), Options()))
    }

    @Test
    fun `a static webp is never claimed`() {
        assertFalse(AnimatedImageByteBufferDecoder(resources).handles(ByteBuffer.wrap(staticWebpHeader()), Options()))
        assertFalse(AnimatedImageStreamDecoder(resources).handles(ByteArrayInputStream(staticWebpHeader()), Options()))
    }
}
