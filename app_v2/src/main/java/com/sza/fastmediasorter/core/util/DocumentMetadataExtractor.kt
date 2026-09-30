package com.sza.fastmediasorter.core.util

import android.content.Context
import io.documentnode.epub4j.domain.Book
import timber.log.Timber
import java.io.File

/**
 * Helper class for extracting metadata from document files (PDF, TXT, EPUB).
 * Handles local files only - network files should be downloaded first.
 */
class DocumentMetadataExtractor(private val context: Context) {
    
    /**
     * Extract PDF metadata from local file.
     *
     * Page count comes from the Android PdfRenderer; all /Info-dict fields come from
     * PdfInfoParser. The two reads are independent - a failure in one does not kill the other.
     */
    fun extractPdfInfo(file: File): DetailedMediaInfo {
        val pageCount: Int? = try {
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { pdfRenderer ->
                    pdfRenderer.pageCount
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read PDF page count: ${file.path}")
            null
        }

        val info = PdfInfoParser.parse(file)

        return DetailedMediaInfo(
            pageCount = pageCount,
            docTitle = info.title,
            docAuthor = info.author,
            pdfVersion = info.version,
            pdfCreator = info.creator,
            pdfProducer = info.producer,
            pdfSubject = info.subject,
            pdfKeywords = info.keywords,
            pdfCreationDate = info.creationDate,
            pdfModificationDate = info.modificationDate
        )
    }
    
    /**
     * Extract text file metadata from local file
     */
    fun extractTextInfo(file: File): DetailedMediaInfo {
        return try {
            val stats = file.bufferedReader().use(TextStatsCounter::count)

            // Try to detect encoding (simplified - always UTF-8 for now)
            val encoding = "UTF-8"

            DetailedMediaInfo(
                lineCount = stats.lines,
                wordCount = stats.words,
                charCount = stats.chars,
                encoding = encoding
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract TXT info: ${file.path}")
            DetailedMediaInfo()
        }
    }
    
    /**
     * Extract EPUB metadata from local file
     */
    fun extractEpubInfo(file: File): DetailedMediaInfo {
        return try {
            EpubLazyReader.withBook(file, ::epubInfoOf)
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract EPUB info: ${file.path}")
            DetailedMediaInfo()
        }
    }
}

/** Shared by the file and the SAF metadata paths; reads only the OPF, never a content entry. */
internal fun epubInfoOf(book: Book): DetailedMediaInfo {
    val title = book.metadata?.titles?.firstOrNull()
    val author = book.metadata?.authors?.firstOrNull()?.let {
        "${it.firstname ?: ""} ${it.lastname ?: ""}".trim()
    }?.ifBlank { null }
    val chapterCount = book.spine?.spineReferences?.size ?: book.tableOfContents?.tocReferences?.size ?: 0

    return DetailedMediaInfo(
        docTitle = title,
        docAuthor = author,
        chapterCount = if (chapterCount > 0) chapterCount else null
    )
}
