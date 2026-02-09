package com.sza.fastmediasorter.data.glide

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Glide decoder for PDF files.
 * Renders the first page of a PDF document as a thumbnail.
 * 
 * Usage:
 * - Register in GlideAppModule
 * - Handles File objects with .pdf extension
 * - Generates thumbnails with proper aspect ratio
 */
class PdfPageDecoder : ResourceDecoder<File, Bitmap> {
    
    override fun handles(source: File, options: Options): Boolean {
        // Only handle PDF files
        return source.name.endsWith(".pdf", ignoreCase = true)
    }

    override fun decode(source: File, width: Int, height: Int, options: Options): Resource<Bitmap>? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        
        try {
            // Open PDF file
            pfd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            
            if (renderer.pageCount == 0) {
                Timber.w("PdfPageDecoder: PDF has no pages: ${source.name}")
                return null
            }
            
            // Open first page
            page = renderer.openPage(0)
            
            // Calculate target dimensions preserving aspect ratio
            val pageWidth = page.width
            val pageHeight = page.height
            val pageAspectRatio = pageWidth.toFloat() / pageHeight.toFloat()
            
            val targetWidth: Int
            val targetHeight: Int
            
            if (width > 0 && height > 0) {
                // Use provided dimensions but preserve aspect ratio
                targetWidth = width
                targetHeight = (width / pageAspectRatio).toInt()
            } else {
                // Fallback to page's original size (scaled down if too large)
                val maxSize = 1024 // Max dimension for thumbnail
                targetWidth = if (pageWidth > maxSize) maxSize else pageWidth
                targetHeight = (targetWidth / pageAspectRatio).toInt()
            }
            
            // Create bitmap with calculated dimensions
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            
            // Fill with white background (standard for PDFs)
            bitmap.eraseColor(Color.WHITE)
            
            // Render PDF page to bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            Timber.d("PdfPageDecoder: Generated thumbnail for ${source.name} (${targetWidth}x${targetHeight})")
            
            return SimpleResource(bitmap)
            
        } catch (e: IOException) {
            Timber.e(e, "PdfPageDecoder: Failed to decode PDF: ${source.name}")
            return null
        } catch (e: SecurityException) {
            // Password-protected PDF
            Timber.w("PdfPageDecoder: PDF is password-protected: ${source.name}")
            return null
        } finally {
            // Clean up resources in correct order
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }
}
