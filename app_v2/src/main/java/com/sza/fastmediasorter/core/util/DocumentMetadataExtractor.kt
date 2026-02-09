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
     * Extract PDF metadata from local file
     */
    fun extractPdfInfo(file: File): DetailedMediaInfo {
        return try {
            val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
            val pageCount = pdfRenderer.pageCount
            pdfRenderer.close()
            pfd.close()
            
            // Basic file statistics only - no metadata extraction to avoid dependency issues
            // File size and modification date are already available in MediaFile object
            
            DetailedMediaInfo(
                pageCount = pageCount
                // All metadata fields left as null - not supported without external libraries
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract PDF info: ${file.path}")
            DetailedMediaInfo()
        }
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
