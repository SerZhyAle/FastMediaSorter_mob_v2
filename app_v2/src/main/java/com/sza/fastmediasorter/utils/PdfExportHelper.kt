package com.sza.fastmediasorter.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.sqrt

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
                    val (width, height) = renderSize(page.width, page.height)
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

    /**
     * Render size for one page: 2x the page's point size for sharper output, capped by
     * [MAX_RENDER_SIDE] and [MAX_RENDER_PIXELS]. Uncapped, a large-format page (A0 at 2x is
     * 128 MB ARGB) throws OutOfMemoryError, which the per-page Exception catch does not stop.
     */
    internal fun renderSize(pageWidth: Int, pageHeight: Int): Pair<Int, Int> {
        val w = pageWidth.coerceAtLeast(1).toDouble()
        val h = pageHeight.coerceAtLeast(1).toDouble()
        val sideScale = MAX_RENDER_SIDE / maxOf(w, h)
        val pixelScale = sqrt(MAX_RENDER_PIXELS / (w * h))
        val scale = minOf(PREFERRED_SCALE, sideScale, pixelScale)
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    private const val PREFERRED_SCALE = 2.0
    private const val MAX_RENDER_SIDE = 4096.0
    private const val MAX_RENDER_PIXELS = 8_388_608.0
    private const val JPEG_QUALITY = 90

    private fun saveBitmapToDownloads(context: Context, bitmap: Bitmap, fileName: String, relativePath: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveBitmapToDownloadsApi29(context, bitmap, fileName, relativePath)
        } else {
            saveBitmapToDownloadsLegacy(bitmap, fileName, relativePath)
        }
    }

    private fun saveBitmapToDownloadsApi29(context: Context, bitmap: Bitmap, fileName: String, relativePath: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
            val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri = target
            val written = target?.let {
                resolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } ?: false
            // Publishing the row after a failed write would leave an empty JPG counted as exported.
            if (target == null || !written) throw IOException("Failed to write $fileName")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(target, contentValues, null, null)
        } catch (e: Exception) {
            // Cleanup on failure
            uri?.let { resolver.delete(it, null, null) }
            throw e
        }
    }

    private fun saveBitmapToDownloadsLegacy(bitmap: Bitmap, fileName: String, relativePath: String) {
        // On API < 29 MediaStore.Downloads does not exist; write directly to external storage.
        // relativePath is "Download/<subfolder>" - strip the leading DIRECTORY_DOWNLOADS segment.
        val subPath = relativePath.removePrefix(Environment.DIRECTORY_DOWNLOADS + File.separator)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloadsDir, subPath).also { it.mkdirs() }
        val outFile = File(exportDir, fileName)

        val written = FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (!written) {
            outFile.delete()
            throw IOException("Failed to write $fileName")
        }
    }
}
