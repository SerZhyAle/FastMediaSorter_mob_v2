package com.sza.fastmediasorter.wear.domain.documents

/**
 * Whether a file is a document at all, which format it is, and whether the watch renders it.
 *
 * S2532: the one place that answers those three questions, so adding a format later touches this
 * class and neither the router nor the screens. It is deliberately not a mime-only decision: the
 * watch lists files from sources that report no mime type - a network share names nothing but the
 * file - and a policy reading only the mime type would turn away readable text whenever the source
 * happened to be silent about it.
 *
 * Stateless and dependency-free so the unit suite can exercise it on the plain JVM, which is where
 * the module's tests run.
 */
class WearDocumentFormatPolicy {

    /**
     * The format of a file already known to be a document, [WearDocumentFormat.OTHER] when nothing
     * matches. Callers that still have to decide whether the file IS a document ask [isDocument].
     */
    fun formatFor(mimeType: String?, fileName: String?): WearDocumentFormat =
        classify(mimeType, fileName) ?: WearDocumentFormat.OTHER

    /** Whether the watch shows this file's content itself, rather than sending the wearer to the phone. */
    fun isReadable(mimeType: String?, fileName: String?): Boolean =
        formatFor(mimeType, fileName).readableOnWatch

    /**
     * Whether the file classifies as a document at all.
     *
     * Separate from [formatFor] because "not a document" and "a document of no known format" need
     * opposite handling: the first keeps the router's audio fallback for a file whose source
     * reported nothing, the second is a refusal that names what it refused.
     */
    fun isDocument(mimeType: String?, fileName: String?): Boolean = classify(mimeType, fileName) != null

    // The mime type decides whenever the source stated one it means; the extension answers only for
    // the sources that state nothing, or state the placeholder every unknown byte stream gets.
    private fun classify(mimeType: String?, fileName: String?): WearDocumentFormat? {
        val mime = mimeType?.trim()?.lowercase().orEmpty()
        return if (mime.isEmpty() || mime == GENERIC_MIME) {
            formatForExtension(fileName)
        } else {
            formatForMime(mime)
        }
    }

    private fun formatForMime(mime: String): WearDocumentFormat? = MIME_FORMATS[mime] ?: when {
        mime.startsWith(TEXT_MIME_PREFIX) -> WearDocumentFormat.PLAIN_TEXT
        // S2006: an unpinned application type is still turned away rather than sent to a player it
        // has no business in - an archive or an installer lands here as much as a document does.
        mime.startsWith(APPLICATION_MIME_PREFIX) -> WearDocumentFormat.OTHER
        else -> null
    }

    private fun formatForExtension(fileName: String?): WearDocumentFormat? {
        val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return EXTENSION_FORMATS[extension]
    }

    private companion object {

        /** What a source hands over when it knows the bytes exist and nothing else about them. */
        const val GENERIC_MIME = "application/octet-stream"

        const val TEXT_MIME_PREFIX = "text/"
        const val APPLICATION_MIME_PREFIX = "application/"

        /**
         * The subtypes worth telling apart from plain text. Everything else under `text/` reads the
         * same on a watch screen, so it falls through to [WearDocumentFormat.PLAIN_TEXT].
         *
         * The office entries repeat `WearContentType.DOCUMENT_MIME_TYPES` rather than importing it:
         * that set answers which category a file is browsed under, this map answers which format it
         * is, and one is allowed to grow without the other.
         */
        val MIME_FORMATS: Map<String, WearDocumentFormat> = mapOf(
            "text/markdown" to WearDocumentFormat.MARKDOWN,
            "text/x-markdown" to WearDocumentFormat.MARKDOWN,
            "text/csv" to WearDocumentFormat.CSV,
            "text/tab-separated-values" to WearDocumentFormat.CSV,
            "text/json" to WearDocumentFormat.JSON,
            "application/json" to WearDocumentFormat.JSON,
            "text/xml" to WearDocumentFormat.XML,
            "application/xml" to WearDocumentFormat.XML,
            "application/pdf" to WearDocumentFormat.PDF,
            "application/epub+zip" to WearDocumentFormat.EPUB,
            "application/rtf" to WearDocumentFormat.OFFICE,
            "application/msword" to WearDocumentFormat.OFFICE,
            "application/vnd.ms-excel" to WearDocumentFormat.OFFICE,
            "application/vnd.ms-powerpoint" to WearDocumentFormat.OFFICE,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to WearDocumentFormat.OFFICE,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to WearDocumentFormat.OFFICE,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to WearDocumentFormat.OFFICE,
            "application/vnd.oasis.opendocument.text" to WearDocumentFormat.OFFICE,
            "application/vnd.oasis.opendocument.spreadsheet" to WearDocumentFormat.OFFICE,
            "application/vnd.oasis.opendocument.presentation" to WearDocumentFormat.OFFICE
        )

        /**
         * Only document extensions are listed. An extension absent from here is not a document, which
         * is what keeps a mime-less audio or image file on its own player instead of on the refusal.
         */
        val EXTENSION_FORMATS: Map<String, WearDocumentFormat> = mapOf(
            "txt" to WearDocumentFormat.PLAIN_TEXT,
            "text" to WearDocumentFormat.PLAIN_TEXT,
            "md" to WearDocumentFormat.MARKDOWN,
            "markdown" to WearDocumentFormat.MARKDOWN,
            "csv" to WearDocumentFormat.CSV,
            "tsv" to WearDocumentFormat.CSV,
            "log" to WearDocumentFormat.LOG,
            "json" to WearDocumentFormat.JSON,
            "xml" to WearDocumentFormat.XML,
            "pdf" to WearDocumentFormat.PDF,
            "epub" to WearDocumentFormat.EPUB,
            "rtf" to WearDocumentFormat.OFFICE,
            "doc" to WearDocumentFormat.OFFICE,
            "docx" to WearDocumentFormat.OFFICE,
            "xls" to WearDocumentFormat.OFFICE,
            "xlsx" to WearDocumentFormat.OFFICE,
            "ppt" to WearDocumentFormat.OFFICE,
            "pptx" to WearDocumentFormat.OFFICE,
            "odt" to WearDocumentFormat.OFFICE,
            "ods" to WearDocumentFormat.OFFICE,
            "odp" to WearDocumentFormat.OFFICE
        )
    }
}
