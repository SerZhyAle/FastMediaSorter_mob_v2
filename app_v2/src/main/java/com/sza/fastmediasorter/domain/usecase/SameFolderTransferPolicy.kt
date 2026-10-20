package com.sza.fastmediasorter.domain.usecase

import java.net.URLDecoder

/**
 * MEDIA-VIEWER rule 4: a move or copy whose destination is the folder the file already lives in is
 * refused. Two resources may point at one folder, so hiding the current resource is not enough.
 */
object SameFolderTransferPolicy {

    private const val CONTENT_SCHEME = "content:"
    private const val DOCUMENT_SEGMENT = "/document/"
    private const val TREE_SEGMENT = "/tree/"

    fun isSameFolder(sourceFilePath: String, destinationFolderPath: String): Boolean {
        val sourceIsContent = sourceFilePath.startsWith(CONTENT_SCHEME)
        val comparable = sourceFilePath.isNotBlank() && destinationFolderPath.isNotBlank() &&
            sourceIsContent == destinationFolderPath.startsWith(CONTENT_SCHEME)
        val sourceParent: String?
        val destination: String?
        if (sourceIsContent) {
            sourceParent = documentId(sourceFilePath)?.let(::documentParentOf)
            destination = documentId(destinationFolderPath)?.let(::normalize)
        } else {
            sourceParent = parentOf(normalize(sourceFilePath))
            destination = normalize(destinationFolderPath)
        }
        return comparable && sourceParent != null && sourceParent == destination
    }

    // "primary:a.jpg" lives in the volume root "primary:", which has no slash to cut at.
    private fun documentParentOf(id: String): String? =
        parentOf(id) ?: id.substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() }?.plus(':')

    // SAF ids encode the path ("primary:DCIM/a.jpg"); the document id beats the tree id when both exist.
    private fun documentId(uri: String): String? {
        val marker = when {
            uri.contains(DOCUMENT_SEGMENT) -> DOCUMENT_SEGMENT
            uri.contains(TREE_SEGMENT) -> TREE_SEGMENT
            else -> return null
        }
        val encoded = uri.substringAfterLast(marker).substringBefore('/')
        return runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
    }

    private fun normalize(path: String): String = path.removePrefix("file://").trimEnd('/')

    private fun parentOf(path: String): String? {
        val trimmed = path.trimEnd('/')
        val cut = trimmed.lastIndexOf('/')
        return if (cut > 0) trimmed.substring(0, cut) else null
    }
}
