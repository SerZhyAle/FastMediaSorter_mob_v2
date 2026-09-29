package com.sza.fastmediasorter.core.util

import android.content.Context
import android.net.Uri
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.epub.EpubReader
import net.sf.jazzlib.ZipFile
import timber.log.Timber
import java.io.File

/**
 * Opens an EPUB for a cover or metadata read without loading the whole book into heap.
 *
 * `EpubReader.readEpub(InputStream)` inflates every archive entry up front, so a thumbnail of an
 * illustrated book costs tens of MB per Glide decode thread. The lazy read keeps each entry as a
 * reference and inflates only what the caller touches.
 */
object EpubLazyReader {

    private const val DEFAULT_ENCODING = "UTF-8"
    private const val SCRATCH_PREFIX = "epub_lazy_"
    private const val SCRATCH_SUFFIX = ".epub"

    /**
     * Lazy resources reopen [file] by path when read, so the book must not escape [block].
     */
    fun <T> withBook(file: File, block: (Book) -> T): T {
        val zipFile = ZipFile(file)
        try {
            return block(EpubReader().readEpubLazy(zipFile, DEFAULT_ENCODING))
        } finally {
            zipFile.close()
        }
    }

    /**
     * A SAF document has no path a zip reader can seek in, so it is copied to a cache file first;
     * the copy goes to disk, not heap. Returns null when the provider yields no stream.
     */
    fun <T> withBook(context: Context, uri: Uri, block: (Book) -> T): T? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            val scratch = File.createTempFile(SCRATCH_PREFIX, SCRATCH_SUFFIX, context.cacheDir)
            try {
                scratch.outputStream().use { stream.copyTo(it) }
                withBook(scratch, block)
            } finally {
                if (!scratch.delete() && scratch.exists()) {
                    Timber.w("EpubLazyReader: could not delete scratch copy ${scratch.name}")
                }
            }
        }
    }
}
