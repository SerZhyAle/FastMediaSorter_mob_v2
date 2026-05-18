package com.sza.fastmediasorter.core.util

import android.content.Context
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
            val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
            val count = pdfRenderer.pageCount
            pdfRenderer.close()
            pfd.close()
            count
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
            val text = file.readText()
            val lines = text.lines().size
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val chars = text.length
            
            // Try to detect encoding (simplified - always UTF-8 for now)
            val encoding = "UTF-8"
            
            DetailedMediaInfo(
                lineCount = lines,
                wordCount = words,
                charCount = chars,
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
            val epubReader = io.documentnode.epub4j.epub.EpubReader()
            val book = file.inputStream().use { epubReader.readEpub(it) }
            
            val title = book.metadata?.titles?.firstOrNull()
            val author = book.metadata?.authors?.firstOrNull()?.let { 
                "${it.firstname ?: ""} ${it.lastname ?: ""}".trim()
            }?.ifBlank { null }
            val chapterCount = book.spine?.spineReferences?.size ?: book.tableOfContents?.tocReferences?.size ?: 0
            
            DetailedMediaInfo(
                docTitle = title,
                docAuthor = author,
                chapterCount = if (chapterCount > 0) chapterCount else null
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract EPUB info: ${file.path}")
            DetailedMediaInfo()
        }
    }
}
