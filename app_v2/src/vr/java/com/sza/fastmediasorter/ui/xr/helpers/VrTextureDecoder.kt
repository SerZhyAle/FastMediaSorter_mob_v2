package com.sza.fastmediasorter.ui.xr.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * S0989: image texture decoding for the diagnostic immersive host, extracted from
 * DiagnosticXrActivity. Owns the reusable direct buffer, budget-driven sampling, Glide-pool bounded
 * decode of the bundled asset and external files, and the bitmap -> RGBA copy. All decode work runs
 * on [Dispatchers.IO].
 */
class VrTextureDecoder(private val context: Context) {

    /** S0989: decoded texture returned by value so the host stays free of decode state. */
    data class Decoded(val bytes: ByteArray, val width: Int, val height: Int)

    @Volatile private var reusableDirectBuffer: ByteBuffer? = null

    @Synchronized
    private fun getReusableDirectBuffer(size: Int): ByteBuffer {
        val current = reusableDirectBuffer
        if (current != null && current.capacity() >= size) {
            current.clear()
            return current
        }
        return try {
            val newBuffer = ByteBuffer.allocateDirect(size)
            reusableDirectBuffer = newBuffer
            newBuffer
        } catch (oom: OutOfMemoryError) {
            Timber.w(oom, "getReusableDirectBuffer: OOM allocating direct buffer of size $size, trying GC...")
            System.gc()
            System.runFinalization()
            ByteBuffer.allocateDirect(size).also { reusableDirectBuffer = it }
        }
    }

    /**
     * S0960: single OOM-guarded path for the direct-buffer -> on-heap RGBA copy shared by the
     * bundled, initial-file, and slide-change decodes. The fresh ByteArray below is the exact
     * allocation that crashed on Quest 3 (128 MB against a warm 512 MB heap), so it degrades to
     * null instead of throwing - callers reuse the existing DecoderFailed / skip-frame fallbacks.
     * The direct-buffer intermediate stays: heap ByteBuffer.wrap produced all-zero pixel output
     * on-device (see VrHudBannerRenderer).
     */
    private fun copyBitmapToRgbaBytes(bitmap: Bitmap): ByteArray? {
        val size = bitmap.width * bitmap.height * RGBA_BYTES_PER_PIXEL
        val buf = getReusableDirectBuffer(size)
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        return try {
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            Timber.d("S0960: RGBA copy ok, ${bytes.size} bytes (${bitmap.width}x${bitmap.height})")
            bytes
        } catch (oom: OutOfMemoryError) {
            Timber.e(oom, "copyBitmapToRgbaBytes: heap cannot fit $size bytes; degrading gracefully")
            null
        }
    }

    /** S0989: decode the bundled equirectangular asset to RGBA bytes; null on terminal failure. */
    suspend fun decodeBundled(): Decoded? = withContext(Dispatchers.IO) {
        val bitmap = decodeBundledPooled() ?: return@withContext null
        try {
            val bytes = copyBitmapToRgbaBytes(bitmap) ?: return@withContext null
            Timber.d("decoded bundled mono 360 asset: ${bitmap.width}x${bitmap.height}")
            Decoded(bytes, bitmap.width, bitmap.height)
        } finally {
            // S0290 Phase 11: return bitmap to Glide pool so the second session reuses the
            // budget-sampled ARGB_8888 allocation instead of re-allocating. Pool drives recycle by LRU.
            returnToPool(bitmap)
        }
    }

    /** S0989: decode an external image file to RGBA bytes; null on terminal failure. */
    suspend fun decodeFile(file: File): Decoded? = withContext(Dispatchers.IO) {
        val bitmap = decodeFilePooled(file) ?: return@withContext null
        try {
            val bytes = copyBitmapToRgbaBytes(bitmap) ?: return@withContext null
            Timber.d("decoded image ${file.name}: ${bitmap.width}x${bitmap.height}")
            Decoded(bytes, bitmap.width, bitmap.height)
        } finally {
            returnToPool(bitmap)
        }
    }

    /**
     * S0290 Phase 11 Step 11.1 / ADR-5 v2 + S0960: decode the bundled equirectangular JPEG via
     * Glide BitmapPool on Dispatchers.IO. The Glide pool is LRU, thread-safe, and already in the
     * project. S0960: the bundled asset now goes through the same [pickSampleSizeForBudget]
     * preflight as external files (8192x4096 exceeds the 96 MB budget -> inSampleSize=2 ->
     * 4096x2048 = 32 MB) - the previous full-size 128 MB decode plus its equally sized on-heap
     * RGBA copy OOMed a warm 512 MB heap on Quest 3. On OOM with `inBitmap` (pool entry too
     * small / GC pressure), retry with a doubled sample size.
     */
    private suspend fun decodeBundledPooled(): Bitmap? = withContext(Dispatchers.IO) {
        val sample = pickSampleSizeForBudget(BUNDLED_WIDTH, BUNDLED_HEIGHT)
        val sampledW = BUNDLED_WIDTH / sample
        val sampledH = BUNDLED_HEIGHT / sample
        Timber.d("S0960: bundled decode preflight sample=$sample -> ${sampledW}x${sampledH}")
        val pool = Glide.get(context).bitmapPool
        val reusable = pool.getDirty(sampledW, sampledH, Bitmap.Config.ARGB_8888)
        val opts = BitmapFactory.Options().apply {
            inBitmap = reusable
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sample
        }
        try {
            context.resources.openRawResource(R.drawable.vr_diagnostic_360_mono).use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (oom: OutOfMemoryError) {
            Timber.w(oom, "VR bundled asset decode OOM with inBitmap; retry with inSampleSize=${sample * 2}")
            opts.inBitmap = null
            opts.inSampleSize = sample * 2
            try {
                context.resources.openRawResource(R.drawable.vr_diagnostic_360_mono).use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            } catch (oom2: OutOfMemoryError) {
                Timber.e(oom2, "VR bundled asset decode OOM even with inSampleSize=${opts.inSampleSize}; giving up")
                null
            }
        }
    }

    /**
     * S0290 Phase 11 Step 11.1: external-file counterpart of [decodeBundledPooled]. Uses
     * `inJustDecodeBounds` preflight to discover dimensions, picks an [inSampleSize] that keeps
     * the ARGB_8888 footprint under [MAX_DECODE_BYTES], then asks the Glide pool for a matching
     * reusable bitmap. Bounds-driven preflight avoids the first OOM that the original
     * implementation took before falling back - observed on Quest 3 with
     * `moraine_lake_flat_mono.jpg` (7742x5327 = 165 MB) which crashed `BitmapFactory` before the
     * catch ran.
     */
    private suspend fun decodeFilePooled(file: File): Bitmap? = withContext(Dispatchers.IO) {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            Timber.w("decodeFilePooled: bounds preflight failed for ${file.name}")
            return@withContext null
        }

        val preflightSample = pickSampleSizeForBudget(boundsOpts.outWidth, boundsOpts.outHeight)
        val sampledW = boundsOpts.outWidth / preflightSample
        val sampledH = boundsOpts.outHeight / preflightSample
        if (preflightSample > 1) {
            Timber.i(
                "decodeFilePooled: ${file.name} bounds ${boundsOpts.outWidth}x${boundsOpts.outHeight}" +
                    " exceeds ${MAX_DECODE_BYTES / (1024 * 1024)} MB budget;" +
                    " preflight inSampleSize=$preflightSample -> ${sampledW}x${sampledH}"
            )
        }

        val pool = Glide.get(context).bitmapPool
        val reusable = pool.getDirty(sampledW, sampledH, Bitmap.Config.ARGB_8888)
        val opts = BitmapFactory.Options().apply {
            inBitmap = reusable
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = preflightSample
        }
        try {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (oom: OutOfMemoryError) {
            Timber.w(oom, "decodeFilePooled: ${file.name} OOM with inBitmap; retry inSampleSize=${preflightSample * 2}")
            opts.inBitmap = null
            opts.inSampleSize = preflightSample * 2
            try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (oom2: OutOfMemoryError) {
                Timber.e(oom2, "decodeFilePooled: ${file.name} OOM at inSampleSize=${opts.inSampleSize}; giving up")
                null
            }
        } catch (iae: IllegalArgumentException) {
            // BitmapFactory throws IllegalArgumentException ("Problem decoding into existing bitmap")
            // when the pool-supplied inBitmap is incompatible with the actual decoded dimensions or
            // config (e.g. moraine_lake_flat_mono.jpg 7742x5327 after inSampleSize may require a
            // different stride). Retry without inBitmap so the decoder allocates a fresh buffer.
            Timber.w(iae, "decodeFilePooled: ${file.name} inBitmap incompatible; retry without pool reuse")
            opts.inBitmap = null
            try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (oom3: OutOfMemoryError) {
                Timber.e(oom3, "decodeFilePooled: ${file.name} OOM on inBitmap-free retry; giving up")
                null
            }
        }
    }

    /**
     * Picks the smallest power-of-2 sample size such that the ARGB_8888 footprint of the
     * resulting bitmap is at most [MAX_DECODE_BYTES]. Capped at 8 - beyond that the picture is
     * below usable VR-quality anyway and we surface the failure.
     */
    private fun pickSampleSizeForBudget(width: Int, height: Int): Int {
        var sample = 1
        var bytes = width.toLong() * height.toLong() * 4L
        while (bytes > MAX_DECODE_BYTES && sample < 8) {
            sample *= 2
            bytes /= 4
        }
        return sample
    }

    /**
     * S0290 Phase 11 Step 11.2: return a bitmap to the Glide pool so the next decode of matching
     * dimensions/Config reuses this allocation. Do NOT call `bitmap.recycle()` - Glide handles LRU
     * eviction internally.
     */
    private fun returnToPool(bitmap: Bitmap) {
        runCatching { Glide.get(context).bitmapPool.put(bitmap) }
            .onFailure { Timber.w(it, "returnToPool: bitmapPool.put threw; falling back to recycle") }
            .onFailure { bitmap.recycle() }
    }

    /** S0989: release the reusable direct buffer on host teardown. */
    fun releaseBuffer() {
        reusableDirectBuffer = null
    }

    companion object {
        // S0290 Phase 11: known source dimensions of the bundled equirectangular asset, pre-sampling.
        // S0960 runs them through pickSampleSizeForBudget, so the Glide BitmapPool key is the sampled
        // size (4096x2048 at the 96 MB budget) and the second + subsequent sessions reuse that
        // allocation instead of re-allocating (root cause of the 2nd-launch OOM observed 2026-05-22).
        private const val BUNDLED_WIDTH = 8192
        private const val BUNDLED_HEIGHT = 4096

        // S0960: bytes per ARGB_8888 / RGBA pixel for buffer sizing.
        private const val RGBA_BYTES_PER_PIXEL = 4

        // S0290 Phase 11.1 + S0960: heap budget for ANY single bitmap decode - external files and the
        // bundled asset alike. Anything above this gets a preflight inSampleSize so we never even try
        // the OOM allocation. 96 MB at ARGB_8888 covers a ~4900x4900 source; the bundled 8192x4096
        // asset lands at inSampleSize=2 (4096x2048 = 32 MB), leaving headroom for ExoPlayer buffers
        // and OpenXR swapchain copies.
        private const val MAX_DECODE_BYTES = 96L * 1024L * 1024L
    }
}
