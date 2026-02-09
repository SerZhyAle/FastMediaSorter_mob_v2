package com.sza.fastmediasorter.data.network.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.util.ByteBufferUtil
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Custom decoder for SafeByteBuffer that avoids Glide's internal stream wrapping.
 * Directly calculates sample size and decodes stream without RecyclableBufferedInputStream.
 */
class SafeByteBufferBitmapDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<SafeByteBuffer, Bitmap> {

    override fun handles(source: SafeByteBuffer, options: Options): Boolean {
        return true
    }

    override fun decode(
        source: SafeByteBuffer,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val buffer = source.buffer
        
        // 1. Calculate sample size (inSampleSize) manually
        // We need to read dimensions first without loading the whole image
        val dimsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        // Use ByteBufferUtil to get a clean stream for bounds
        // Note: ByteBufferUtil.toStream returns a customized stream that doesn't reset position of original buffer
        // IF we use the buffer version that duplicates it.
        // But here we want to be safe.
        
        try {
            // Convert ByteBuffer to byte array for seekable stream
            // This is necessary for progressive JPEG support
            buffer.position(0)
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            buffer.position(0)
            
            Timber.d("SafeByteBufferBitmapDecoder: Starting decode - size=${bytes.size / 1024}KB, reqWidth=$width, reqHeight=$height")
            
            // Get stream for bounds (seekable)
            val streamForBounds = java.io.ByteArrayInputStream(bytes)
            BitmapFactory.decodeStream(streamForBounds, null, dimsOptions)
            
            Timber.d("SafeByteBufferBitmapDecoder: Bounds decoded - width=${dimsOptions.outWidth}, height=${dimsOptions.outHeight}, mimeType=${dimsOptions.outMimeType}")
            
            // Calculate inSampleSize
            val sampleSize = calculateInSampleSize(dimsOptions, width, height)
            
            // 2. Decode actual image
            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            // Create new seekable stream for decode (supports progressive JPEG)
            val streamForDecode = java.io.ByteArrayInputStream(bytes)
            
            val bitmap = BitmapFactory.decodeStream(streamForDecode, null, decodeOptions)
            
            if (bitmap == null) {
                Timber.e("SafeByteBufferBitmapDecoder: Failed to decode bitmap - BitmapFactory returned null (size=${bytes.size / 1024}KB, bounds=${dimsOptions.outWidth}x${dimsOptions.outHeight}, mime=${dimsOptions.outMimeType})")
                return null
            }
            
            Timber.d("SafeByteBufferBitmapDecoder: Decoded bitmap ${bitmap.width}x${bitmap.height} (sample=$sampleSize) from ${buffer.capacity() / 1024}KB")
            
            return BitmapResource.obtain(bitmap, bitmapPool)
            
        } catch (e: Exception) {
            Timber.e(e, "SafeByteBufferBitmapDecoder: Exception during decode (size=${buffer.capacity() / 1024}KB)")
            throw IOException("Failed to decode SafeByteBuffer", e)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (reqHeight == com.bumptech.glide.request.target.Target.SIZE_ORIGINAL || 
            reqWidth == com.bumptech.glide.request.target.Target.SIZE_ORIGINAL) {
            return 1
        }

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
