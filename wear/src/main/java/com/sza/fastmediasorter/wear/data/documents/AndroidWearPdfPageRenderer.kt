package com.sza.fastmediasorter.wear.data.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.sza.fastmediasorter.wear.domain.repository.WearPdfDocument
import com.sza.fastmediasorter.wear.domain.repository.WearPdfPageRenderer
import com.sza.fastmediasorter.wear.util.CloseableHandOff
import com.sza.fastmediasorter.wear.util.handingOffCloseable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * S4227: [WearPdfPageRenderer] over the platform's [PdfRenderer], present on Wear OS since API 21.
 *
 * The platform renderer keeps one page open at a time and is not thread-safe, so every page goes through one
 * [Mutex]; three drawn pages are kept so scrolling back and forth does not redraw them (research 05).
 */
class AndroidWearPdfPageRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : WearPdfPageRenderer {

    override suspend fun open(uri: Uri): WearPdfDocument? = handingOffCloseable { handOff ->
        withContext(Dispatchers.IO) { openTracked(uri, handOff) }
    }

    private fun openTracked(uri: Uri, handOff: CloseableHandOff): WearPdfDocument? {
        var descriptor: ParcelFileDescriptor? = null
        return try {
            descriptor = context.contentResolver.openFileDescriptor(uri, READ_ONLY)
            descriptor?.let { handOff.track(PlatformPdfDocument(it, PdfRenderer(it))) }
        } catch (e: IOException) {
            Timber.w("PDF: cannot be opened (%s)", e.javaClass.simpleName)
            descriptor?.close()
            null
        } catch (e: SecurityException) {
            // A password-protected PDF lands here as well as a file this process may not read.
            Timber.w("PDF: refused (%s)", e.javaClass.simpleName)
            descriptor?.close()
            null
        }
    }

    private class PlatformPdfDocument(
        private val descriptor: ParcelFileDescriptor,
        private val renderer: PdfRenderer,
    ) : WearPdfDocument {

        private val lock = Mutex()
        private val drawn = LruCache<Int, Bitmap>(KEPT_PAGES)
        private var closed = false

        override val pageCount: Int = renderer.pageCount

        override suspend fun page(index: Int, widthPx: Int): Bitmap? = lock.withLock {
            drawn.get(index)?.takeIf { it.width == widthPx }
                ?: withContext(Dispatchers.IO) { draw(index, widthPx) }?.also { drawn.put(index, it) }
        }

        private fun draw(index: Int, widthPx: Int): Bitmap? {
            if (closed || index !in 0 until pageCount || widthPx <= 0) return null
            return renderer.openPage(index).use { page ->
                val height = (widthPx.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }

        override fun close() {
            if (!closed) {
                closed = true
                drawn.evictAll()
                renderer.close()
                descriptor.close()
            }
        }
    }

    private companion object {
        const val READ_ONLY = "r"
        const val KEPT_PAGES = 3
    }
}
