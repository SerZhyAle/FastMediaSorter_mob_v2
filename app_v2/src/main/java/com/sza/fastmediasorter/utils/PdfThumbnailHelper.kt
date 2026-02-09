package com.sza.fastmediasorter.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfThumbnailHelper {

    suspend fun generateThumbnail(file: File, width: Int, height: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            var fileDescriptor: ParcelFileDescriptor? = null
            var pdfRenderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            
            try {
                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor)
                
                if (pdfRenderer.pageCount > 0) {
                    page = pdfRenderer.openPage(0)
                    
                    // Calculate scaling
                    // Target aspect ratio
                    val targetRatio = width.toFloat() / height
                    // Page aspect ratio
                    val pageRatio = page.width.toFloat() / page.height
                    
                    val renderWidth: Int
                    val renderHeight: Int
                    
                    if (targetRatio > pageRatio) {
                        // Target is wider than page, fit height
                        renderHeight = height
                        renderWidth = (height * pageRatio).toInt()
                    } else {
                        // Target is taller than page, fit width
                        renderWidth = width
                        renderHeight = (width / pageRatio).toInt()
                    }
                    
                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE) // PDFs usually have transparent background
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return@withContext bitmap
                }
                return@withContext null
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate PDF thumbnail for ${file.absolutePath}")
                return@withContext null
            } finally {
                try {
                    page?.close()
                    pdfRenderer?.close()
                    fileDescriptor?.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        }
    }
}
