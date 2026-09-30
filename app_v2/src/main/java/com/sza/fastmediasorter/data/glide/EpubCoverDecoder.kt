package com.sza.fastmediasorter.data.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.sza.fastmediasorter.FastMediaSorterApp
import com.sza.fastmediasorter.core.util.EpubLazyReader
import com.sza.fastmediasorter.utils.SafHelper
import timber.log.Timber
import java.io.File

/**
 * Glide decoder for EPUB files.
 * Extracts the cover image from an EPUB e-book as a thumbnail.
 * 
 * Usage:
 * - Register in GlideAppModule
 * - Handles File objects with .epub extension (including SAF content URIs)
 * - Extracts cover image from EPUB metadata
 */
class EpubCoverDecoder(
    private val context: Context = FastMediaSorterApp.appContext
) : ResourceDecoder<File, Bitmap> {
    
    override fun handles(source: File, options: Options): Boolean {
        // Only handle EPUB files (supports direct filesystem paths and SAF content URIs)
        val cleanPath = source.path.substringBefore('?').substringBefore('#')
        return cleanPath.endsWith(".epub", ignoreCase = true)
    }

    override fun decode(source: File, width: Int, height: Int, options: Options): Resource<Bitmap>? {
        try {
            val imageData: ByteArray? = if (SafHelper.isContentUri(source.path)) {
                val uri = SafHelper.parseUri(source.path)
                EpubLazyReader.withBook(context, uri) { it.coverImage?.data }
            } else {
                EpubLazyReader.withBook(source) { it.coverImage?.data }
            }

            if (imageData == null) {
                Timber.w("EpubCoverDecoder: No cover image found in EPUB: ${source.name}")
                return null
            }

            val decodeOptions = BitmapFactory.Options()
            
            // First decode to get dimensions
            decodeOptions.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size, decodeOptions)
            
            val originalWidth = decodeOptions.outWidth
            val originalHeight = decodeOptions.outHeight
            
            // Calculate sample size for downscaling if needed
            var sampleSize = 1
            if (width > 0 && height > 0) {
                // Calculate inSampleSize to scale down image
                while (originalWidth / sampleSize > width || originalHeight / sampleSize > height) {
                    sampleSize *= 2
                }
            }
            
            // Decode actual bitmap with sample size
            decodeOptions.inJustDecodeBounds = false
            decodeOptions.inSampleSize = sampleSize
            decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
            
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, decodeOptions)
            
            if (bitmap == null) {
                Timber.w("EpubCoverDecoder: Failed to decode cover image for: ${source.name}")
                return null
            }
            
            Timber.d("EpubCoverDecoder: Extracted cover for ${source.name} (${bitmap.width}x${bitmap.height})")
            
            return SimpleResource(bitmap)
            
        } catch (e: Exception) {
            Timber.e(e, "EpubCoverDecoder: Failed to extract cover from EPUB: ${source.name}")
            return null
        }
    }
}
