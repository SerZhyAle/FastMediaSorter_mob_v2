package com.sza.fastmediasorter.data.glide

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.util.ByteBufferUtil
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Glide decoders that turn ANIMATED WebP / APNG into a platform [android.graphics.drawable.AnimatedImageDrawable]
 * via [ImageDecoder] (API 28+), so the image viewer animates them like GIFs.
 *
 * Scoping: [handles] returns true only when a header sniff detects an *animated* WebP (VP8X
 * animation bit) or APNG (`acTL` chunk). Static WebP/PNG and every other format return false, so
 * Glide's built-in downsampler keeps decoding them (and static WebP keeps its normal, non-animated
 * path -> no bogus animated badge). The decoders are only registered on API 28+; below that these
 * files fall back to Glide's static first-frame decode (unchanged).
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class AnimatedImageByteBufferDecoder : ResourceDecoder<ByteBuffer, Drawable> {

    override fun handles(source: ByteBuffer, options: Options): Boolean {
        val probe = source.duplicate()
        val length = minOf(probe.remaining(), AnimatedImageHeaderSniffer.HEADER_PROBE_BYTES)
        val header = ByteArray(length)
        probe.get(header, 0, length)
        return AnimatedImageHeaderSniffer.isAnimated(header, length)
    }

    override fun decode(source: ByteBuffer, width: Int, height: Int, options: Options): Resource<Drawable>? =
        decodeAnimatedDrawable(ImageDecoder.createSource(source), width, height)
}

/**
 * Stream-model sibling of [AnimatedImageByteBufferDecoder]. Glide rewinds the InputStream between
 * decoder probes, so reading the header in [handles] and the full stream in [decode] is safe.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class AnimatedImageStreamDecoder : ResourceDecoder<InputStream, Drawable> {

    override fun handles(source: InputStream, options: Options): Boolean {
        val header = ByteArray(AnimatedImageHeaderSniffer.HEADER_PROBE_BYTES)
        var read = 0
        while (read < header.size) {
            val count = source.read(header, read, header.size - read)
            if (count < 0) break
            read += count
        }
        return AnimatedImageHeaderSniffer.isAnimated(header, read)
    }

    override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<Drawable>? {
        val buffer = try {
            ByteBufferUtil.fromStream(source)
        } catch (e: IOException) {
            Timber.w(e, "AnimatedImageStreamDecoder: failed to buffer stream")
            return null
        }
        return decodeAnimatedDrawable(ImageDecoder.createSource(buffer), width, height)
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimatedDrawable(imageSource: ImageDecoder.Source, width: Int, height: Int): Resource<Drawable>? =
    try {
        val drawable = ImageDecoder.decodeDrawable(imageSource) { decoder, info, _ ->
            val sample = AnimatedImageHeaderSniffer.sampleSize(
                info.size.width, info.size.height, width, height,
            )
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        AnimatedImageDrawableResource(drawable)
    } catch (e: IOException) {
        // Corrupt/partial animated file - let Glide surface the load failure, don't crash decode.
        Timber.w(e, "AnimatedImageDecoder: decodeDrawable failed")
        null
    }

/**
 * [Resource] wrapper reporting resource class [Drawable] so the request's Drawable transcode path
 * resolves regardless of the concrete AnimatedImageDrawable runtime type.
 */
private class AnimatedImageDrawableResource(private val drawable: Drawable) : Resource<Drawable> {

    override fun getResourceClass(): Class<Drawable> = Drawable::class.java

    override fun get(): Drawable = drawable

    override fun getSize(): Int {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        return if (w > 0 && h > 0) w * h * BYTES_PER_ARGB_PIXEL else FALLBACK_SIZE_BYTES
    }

    override fun recycle() {
        // AnimatedImageDrawable is freed by GC; just stop playback so an evicted resource does not
        // keep animating off-screen. No native buffer to release explicitly (unlike GifDrawable).
        (drawable as? Animatable)?.takeIf { it.isRunning }?.stop()
    }

    private companion object {
        const val BYTES_PER_ARGB_PIXEL = 4
        const val FALLBACK_SIZE_BYTES = 1
    }
}

/** Header signatures for animated WebP (VP8X animation bit) and APNG (`acTL` chunk). */
private object AnimatedImageHeaderSniffer {

    const val HEADER_PROBE_BYTES = 64

    private val ACTL_CHUNK = "acTL".toByteArray(Charsets.US_ASCII)

    private const val WEBP_RIFF_OFFSET = 0
    private const val WEBP_WEBP_OFFSET = 8
    private const val WEBP_VP8X_OFFSET = 12
    private const val WEBP_VP8X_FLAGS_OFFSET = 20
    private const val WEBP_ANIMATION_BIT = 0x02
    private const val WEBP_MIN_HEADER = 21
    private const val PNG_TAG_OFFSET = 1

    fun isAnimated(header: ByteArray, length: Int): Boolean =
        isAnimatedWebp(header, length) || isApng(header, length)

    private fun isAnimatedWebp(header: ByteArray, length: Int): Boolean {
        if (length < WEBP_MIN_HEADER) return false
        if (!matches(header, WEBP_RIFF_OFFSET, "RIFF")) return false
        if (!matches(header, WEBP_WEBP_OFFSET, "WEBP")) return false
        if (!matches(header, WEBP_VP8X_OFFSET, "VP8X")) return false
        return header[WEBP_VP8X_FLAGS_OFFSET].toInt() and WEBP_ANIMATION_BIT != 0
    }

    // "PNG" (ASCII) sits at bytes 1..3 of the 8-byte PNG signature; combined with an `acTL` chunk
    // that uniquely identifies APNG without hard-coding the non-printable signature bytes.
    private fun isApng(header: ByteArray, length: Int): Boolean {
        if (!matches(header, PNG_TAG_OFFSET, "PNG")) return false
        return indexOf(header, length, ACTL_CHUNK) >= 0
    }

    /** Sub-2 power-of-two sample size that keeps the decoded size >= the requested bounds. */
    fun sampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) return 1
        var sample = 1
        while (srcWidth / (sample * 2) >= reqWidth && srcHeight / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    private fun matches(header: ByteArray, offset: Int, ascii: String): Boolean {
        if (offset + ascii.length > header.size) return false
        for (i in ascii.indices) {
            if (header[offset + i] != ascii[i].code.toByte()) return false
        }
        return true
    }

    private fun indexOf(header: ByteArray, length: Int, pattern: ByteArray): Int {
        if (pattern.isEmpty() || length < pattern.size) return -1
        val last = length - pattern.size
        for (start in 0..last) {
            var matched = true
            for (i in pattern.indices) {
                if (header[start + i] != pattern[i]) {
                    matched = false
                    break
                }
            }
            if (matched) return start
        }
        return -1
    }
}
