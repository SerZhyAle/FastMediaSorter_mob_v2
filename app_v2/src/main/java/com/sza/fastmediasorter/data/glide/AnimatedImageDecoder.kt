package com.sza.fastmediasorter.data.glide

import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.util.ByteBufferUtil
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Glide decoders that turn animated WebP / APNG into a platform drawable via [ImageDecoder] (API 28+).
 *
 * Scoping: [handles] returns true only when a header sniff detects an *animated* WebP (VP8X
 * animation bit) or APNG (`acTL` chunk). Static WebP/PNG and every other format return false, so
 * Glide's built-in downsampler keeps decoding them (and static WebP keeps its normal, non-animated
 * path -> no bogus animated badge). The decoders are only registered on API 28+; below that these
 * files fall back to Glide's static first-frame decode (unchanged).
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class AnimatedImageByteBufferDecoder(
    private val resources: Resources,
) : ResourceDecoder<ByteBuffer, Drawable> {

    override fun handles(source: ByteBuffer, options: Options): Boolean {
        val probe = source.duplicate()
        val length = minOf(probe.remaining(), AnimatedImageHeaderSniffer.HEADER_PROBE_BYTES)
        val header = ByteArray(length)
        probe.get(header, 0, length)
        return AnimatedImageHeaderSniffer.isAnimated(header, length)
    }

    override fun decode(source: ByteBuffer, width: Int, height: Int, options: Options): Resource<Drawable>? {
        return if (isAnimationDisabled(options)) {
            decodeStillFrame(ImageDecoder.createSource(source), width, height, resources)
        } else {
            decodeAnimatedDrawable(ImageDecoder.createSource(source), width, height)
        }
    }
}

/**
 * Stream-model sibling of [AnimatedImageByteBufferDecoder]. Glide rewinds the InputStream between
 * decoder probes, so reading the header in [handles] and the full stream in [decode] is safe.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class AnimatedImageStreamDecoder(
    private val resources: Resources,
) : ResourceDecoder<InputStream, Drawable> {

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
        return if (isAnimationDisabled(options)) {
            decodeStillFrame(ImageDecoder.createSource(buffer), width, height, resources)
        } else {
            decodeAnimatedDrawable(ImageDecoder.createSource(buffer), width, height)
        }
    }
}

/**
 * A caller that asked for a still (`dontAnimate()`, which every thumbnail request uses) must receive
 * a still-frame resource rather than an animated drawable. The earlier custom decoder must therefore
 * produce the first frame itself when the request disables animation.
 */
private fun isAnimationDisabled(options: Options): Boolean {
    return options.get(GifOptions.DISABLE_ANIMATION) == true
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeStillFrame(
    imageSource: ImageDecoder.Source,
    width: Int,
    height: Int,
    resources: Resources,
): Resource<Drawable>? =
    try {
        Timber.d("S1317: still-frame decode, req=%dx%d", width, height)
        val bitmap = ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val sample = AnimatedImageHeaderSniffer.sampleSize(
                info.size.width,
                info.size.height,
                width,
                height,
            )
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        BitmapDrawableResource(BitmapDrawable(resources, bitmap))
    } catch (e: IOException) {
        Timber.w(e, "AnimatedImageDecoder: decodeBitmap failed")
        null
    }

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimatedDrawable(imageSource: ImageDecoder.Source, width: Int, height: Int): Resource<Drawable>? =
    try {
        val drawable = ImageDecoder.decodeDrawable(imageSource) { decoder, info, _ ->
            // S1026: ALLOCATOR_DEFAULT yields a HARDWARE-backed drawable, and Glide's transform /
            // cache-encode step then dies on "Cannot create a mutable Bitmap with config: HARDWARE".
            // Software allocation costs heap but is the only config those steps can copy.
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val sample = AnimatedImageHeaderSniffer.sampleSize(
                info.size.width,
                info.size.height,
                width,
                height,
            )
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        Timber.d("S1026: decoded animated drawable, req=%dx%d", width, height)
        AnimatedImageDrawableResource(drawable)
    } catch (e: IOException) {
        // Corrupt/partial animated file - let Glide surface the load failure, don't crash decode.
        Timber.w(e, "AnimatedImageDecoder: decodeDrawable failed")
        null
    }

/**
 * [Resource] wrapper reporting the concrete [BitmapDrawable] class (not [Drawable]) so Glide's own
 * `BitmapDrawableEncoder` - registered against `BitmapDrawable::class.java` - resolves for this
 * still-frame result and writes it to the disk cache, same as any other static image.
 */
private class BitmapDrawableResource(private val drawable: BitmapDrawable) : Resource<Drawable> {

    @Suppress("UNCHECKED_CAST")
    override fun getResourceClass(): Class<Drawable> = BitmapDrawable::class.java as Class<Drawable>

    override fun get(): Drawable = drawable

    override fun getSize(): Int = drawable.bitmap?.allocationByteCount ?: 0

    override fun recycle() = Unit
}

/**
 * [Resource] wrapper reporting the generic [Drawable] class rather than the concrete
 * `AnimatedImageDrawable` runtime type - Glide has no encoder for that concrete type, so the
 * broader [Drawable] registration matches [AnimatedImageDrawableNoOpEncoder] instead, letting the
 * load complete uncached (see that encoder's KDoc for why a no-op is required here).
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
