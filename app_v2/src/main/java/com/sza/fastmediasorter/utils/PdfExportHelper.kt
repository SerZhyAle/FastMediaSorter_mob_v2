package com.sza.fastmediasorter.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object PdfExportHelper {

    /**
     * Exports all pages of a PDF file as JPG images to the Downloads/FastMediaSorter_Exports directory.
     * @param context Application context
     * @param pdfFile The PDF file to export
     * @return Result containing the count of exported pages or an error
     */
    suspend fun exportPdfPagesToJpg(context: Context, pdfFile: File): Result<Int> = withContext(Dispatchers.IO) {
        var renderer: PdfRenderer? = null
        var fd: ParcelFileDescriptor? = null
        var exportedCount = 0

        try {
            fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)

            val pageCount = renderer.pageCount
            val baseName = pdfFile.nameWithoutExtension
            // Use a specific subfolder in Downloads to be organized
            val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + "FastMediaSorter_Exports" + File.separator + baseName

            for (i in 0 until pageCount) {
                var page: PdfRenderer.Page? = null
                var bitmap: Bitmap? = null
                
                try {
                    page = renderer.openPage(i)
                    // Use higher resolution (2x original density) for better quality
                    val width = page.width * 2
                    val height = page.height * 2
                    
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    // PDF pages are transparent by default, so we need a white background
                    bitmap.eraseColor(Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val fileName = "${baseName}_page_${i + 1}.jpg"
                    
                    saveBitmapToDownloads(context, bitmap, fileName, relativePath)
                    
                    exportedCount++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to render/save page ${i + 1}")
                } finally {
                    bitmap?.recycle()
                    page?.close()
                }
            }
            
            if (exportedCount > 0) {
                Result.success(exportedCount)
            } else {
                Result.failure(Exception("No pages were exported"))
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize PDF renderer")
            Result.failure(e)
        } finally {
            try {
                renderer?.close()
            } catch (e: Exception) { Timber.w(e, "Error closing renderer") }
            
            try {
                fd?.close()
            } catch (e: Exception) { Timber.w(e, "Error closing file descriptor") }
        }
    }

    private fun saveBitmapToDownloads(context: Context, bitmap: Bitmap, fileName: String, relativePath: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
             uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
             uri?.let {
                 resolver.openOutputStream(it)?.use { out ->
                     bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                 }
                 
                 contentValues.clear()
                 contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                 resolver.update(it, contentValues, null, null)
             } ?: throw Exception("Failed to create MediaStore entry")
        } catch (e: Exception) {
            // Cleanup on failure
            uri?.let { resolver.delete(it, null, null) }
            throw e
        }
    }
}
