package com.sza.fastmediasorter.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber
import java.util.Locale

/**
 * Helper utility for Storage Access Framework (SAF) operations.
 * Provides consistent handling of content:// URIs across the application.
 */
object SafHelper {

    /**
     * Delete a file via SAF (Storage Access Framework).
     * Tries multiple methods in order: ContentResolver.delete, DocumentsContract, DocumentFile.
     *
     * @param context Application context
     * @param contentUri The content:// URI of the file to delete
     * @param tag Optional tag for logging (e.g., "SMB executeMove")
     * @return true if deletion was successful, false otherwise
     */
    fun deleteContentUri(context: Context, contentUri: String, tag: String = "SafHelper"): Boolean {
        return try {
            // Normalize content URI for deletion (ensure content://)
            val normalizedUri = if (contentUri.startsWith("content://")) {
                contentUri
            } else {
                contentUri.replaceFirst("content:/", "content://")
            }
            val uri = Uri.parse(normalizedUri)
            
            Timber.d("$tag: Attempting to delete URI: $normalizedUri")
            Timber.d("$tag: URI authority: ${uri.authority}, path: ${uri.path}")

            // Try multiple deletion methods for SAF
            var deleted = false

            // Method 1: ContentResolver.delete
            if (!deleted) {
                try {
                    val rowsDeleted = context.contentResolver.delete(uri, null, null)
                    deleted = rowsDeleted > 0
                    Timber.d("$tag: ContentResolver.delete returned: $rowsDeleted")
                } catch (e: Exception) {
                    Timber.w("$tag: ContentResolver.delete failed: ${e.message}")
                }
            }

            // Method 2: DocumentsContract.deleteDocument (best for document URIs)
            if (!deleted) {
                try {
                    deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                    Timber.d("$tag: DocumentsContract.deleteDocument returned: $deleted")
                } catch (e: Exception) {
                    Timber.w("$tag: DocumentsContract.deleteDocument failed: ${e.message}")
                }
            }

            // Method 3: DocumentFile.fromSingleUri (for simple document URIs)
            if (!deleted) {
                try {
                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    if (docFile != null && docFile.exists()) {
                        deleted = docFile.delete()
                        Timber.d("$tag: DocumentFile.fromSingleUri.delete returned: $deleted")
                    } else {
                        Timber.w("$tag: DocumentFile.fromSingleUri returned null or doesn't exist")
                    }
                } catch (e: Exception) {
                    Timber.w("$tag: DocumentFile.fromSingleUri.delete failed: ${e.message}")
                }
            }
            
            // Method 4: Extract tree URI and navigate to document (for tree document URIs)
            if (!deleted && contentUri.contains("/tree/") && contentUri.contains("/document/")) {
                try {
                    Timber.d("$tag: Detected tree document URI, attempting tree-based deletion")
                    // Extract tree URI: content://...externalstorage.../tree/primary%3A...
                    val treeStartIndex = contentUri.indexOf("/tree/")
                    val documentStartIndex = contentUri.indexOf("/document/")
                    
                    if (treeStartIndex > 0 && documentStartIndex > treeStartIndex) {
                        val treeUriString = contentUri.substring(0, documentStartIndex)
                        val treeUri = Uri.parse(treeUriString)
                        
                        // Get the document ID from the full URI
                        val documentId = DocumentsContract.getDocumentId(uri)
                        
                        Timber.d("$tag: Tree URI: $treeUriString")
                        Timber.d("$tag: Document ID: $documentId")
                        
                        // Get DocumentFile for tree root
                        val treeRoot = DocumentFile.fromTreeUri(context, treeUri)
                        if (treeRoot != null) {
                            // Find the specific document within the tree
                            // DocumentFile.fromTreeUri with document ID
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            Timber.d("$tag: Built document URI: $documentUri")
                            
                            deleted = DocumentsContract.deleteDocument(context.contentResolver, documentUri)
                            Timber.i("$tag: Tree-based deletion returned: $deleted")
                        } else {
                            Timber.w("$tag: fromTreeUri returned null for: $treeUri")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$tag: Tree-based deletion failed")
                }
            }

            if (!deleted) {
                Timber.e("$tag: All deletion methods failed for URI: $contentUri")
            } else {
                Timber.i("$tag: Successfully deleted URI: $contentUri")
                notifyMediaScannerAfterDelete(context, contentUri, tag)
            }

            deleted
        } catch (e: Exception) {
            Timber.e(e, "$tag: Failed to delete SAF URI: $contentUri")
            false
        }
    }

    /**
     * Unindex deleted file from MediaStore so it doesn't linger in queries.
     */
    private fun notifyMediaScannerAfterDelete(context: Context, contentUri: String, tag: String) {
        try {
            var filePath: String? = if (!contentUri.startsWith("content://")) contentUri else null
            if (filePath == null) {
                val uri = Uri.parse(contentUri)
                val docId = try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { null }
                if (docId != null && docId.contains(":")) {
                    val relativePath = docId.substringAfter(":")
                    filePath = "/storage/emulated/0/$relativePath"
                }
            }

            if (filePath != null && filePath.startsWith("/")) {
                context.contentResolver.delete(
                    android.provider.MediaStore.Files.getContentUri("external"),
                    "${android.provider.MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(filePath)
                )
                android.media.MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
                Timber.d("$tag: Unindexed deleted file from MediaStore: $filePath")
            }
        } catch (e: Exception) {
            Timber.w(e, "$tag: Failed to unindex file from MediaStore after delete")
        }
    }

    /**
     * Check if a path is a content URI (SAF).
     *
     * @param path The path to check
     * @return true if path starts with content:/ or content://
     */
    fun isContentUri(path: String): Boolean {
        return path.startsWith("content:/") || path.startsWith("content:\\")
    }

    /**
     * Normalize a content URI to ensure it has the proper content:// prefix.
     *
     * @param path The content path to normalize
     * @return Normalized URI string with content:// prefix
     */
    fun normalizeContentUri(path: String): String {
        val forwardPath = path.replace('\\', '/')
        return if (forwardPath.startsWith("content://")) {
            forwardPath
        } else {
            forwardPath.replaceFirst("content:/", "content://")
        }
    }

    /**
     * Parse URI string with automatic sanitization for malformed content:// URIs.
     * This is the recommended way to parse URIs that may come from SAF or user input.
     *
     * @param uriString The URI string to parse
     * @return Parsed and sanitized Uri object
     */
    fun parseUri(uriString: String): Uri {
        val forwardPath = uriString.replace('\\', '/')
        val normalized = if (forwardPath.startsWith("content:") && !forwardPath.startsWith("content://")) {
            forwardPath.replaceFirst("content:/", "content://")
        } else {
            forwardPath
        }
        return Uri.parse(normalized)
    }

    /**
     * Get the display name of a file from a content URI.
     *
     * @param context Application context
     * @param contentUri The content:// URI
     * @return The display name or null if not found
     */
    fun getDisplayName(context: Context, contentUri: String): String? {
        return try {
            val uri = Uri.parse(normalizeContentUri(contentUri))
            val docFile = DocumentFile.fromSingleUri(context, uri)
            docFile?.name
        } catch (e: Exception) {
            Timber.w(e, "SafHelper: Failed to get display name for: $contentUri")
            null
        }
    }

    /**
     * Get the file size from a content URI.
     *
     * @param context Application context
     * @param contentUri The content:// URI
     * @return File size in bytes, or -1 if not available
     */
    fun getFileSize(context: Context, contentUri: String): Long {
        return try {
            val uri = Uri.parse(normalizeContentUri(contentUri))
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.length
            } ?: -1L
        } catch (e: Exception) {
            Timber.w(e, "SafHelper: Failed to get file size for: $contentUri")
            -1L
        }
    }

    /**
     * Get DocumentFile from URI string.
     * Tries detection if it's a Tree URI or Single URI.
     * 
     * @param context Application context
     * @param uri The URI to wrap
     * @return DocumentFile or null if invalid
     */
    fun getDocumentFileFromUri(context: Context, uri: Uri): DocumentFile? {
        return try {
            if (DocumentFile.isDocumentUri(context, uri)) {
                DocumentFile.fromSingleUri(context, uri)
            } else {
                DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
            }
        } catch (e: Exception) {
            Timber.w(e, "SafHelper: Failed to get DocumentFile for: $uri")
            null
        }
    }

    /**
     * @return true when [uri] points to a tree granted by ACTION_OPEN_DOCUMENT_TREE.
     */
    fun isTreeUri(uri: Uri): Boolean {
        return runCatching { DocumentsContract.isTreeUri(uri) }.getOrDefault(false)
    }

    /**
     * Resolve a writable tree root from a persisted tree URI string.
     */
    fun getTreeRoot(context: Context, treeUriString: String): DocumentFile? {
        return try {
            val treeUri = parseUri(treeUriString)
            if (!isTreeUri(treeUri)) {
                Timber.w("SafHelper: URI is not a tree URI: $treeUri")
                return null
            }

            DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.exists() && it.canWrite() }
        } catch (e: Exception) {
            Timber.w(e, "SafHelper: Failed to resolve tree root for: $treeUriString")
            null
        }
    }

    /**
     * Find an existing writable child under a tree root.
     */
    fun findChildInTree(
        context: Context,
        treeUriString: String,
        displayName: String
    ): DocumentFile? {
        val root = getTreeRoot(context, treeUriString) ?: return null
        return root.findFile(displayName)
    }

    /**
     * Find or create a writable child file under a tree URI.
     */
    fun getOrCreateWritableChildFile(
        context: Context,
        treeUriString: String,
        displayName: String,
        overwrite: Boolean,
        mimeType: String = guessMimeType(displayName)
    ): DocumentFile? {
        val root = getTreeRoot(context, treeUriString) ?: return null
        val existing = root.findFile(displayName)

        if (existing != null) {
            if (existing.isDirectory) {
                Timber.w("SafHelper: Existing child is a directory, not a file: $displayName")
                return null
            }
            if (!overwrite) {
                return existing
            }
            if (!existing.delete()) {
                Timber.w("SafHelper: Failed to replace existing child: $displayName")
                return null
            }
        }

        return root.createFile(mimeType, displayName)
    }

    /**
     * True for SAF tree grants rooted in Android/data or Android/obb.
     */
    fun isRestrictedTreeUri(treeUriString: String): Boolean {
        return try {
            val treeUri = parseUri(treeUriString)
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val relativePath = Uri.decode(documentId.substringAfter(':', "")).trim('/')
            relativePath.equals("Android/data", ignoreCase = true) ||
                relativePath.startsWith("Android/data/", ignoreCase = true) ||
                relativePath.equals("Android/obb", ignoreCase = true) ||
                relativePath.startsWith("Android/obb/", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * S1378: MIME type for a name about to be created through SAF. `DocumentFile.createFile` needs one
     * up front and a wrong guess is sticky - the provider stores it on the document - so this single
     * guess is shared by every SAF creation path rather than re-derived per call site.
     */
    fun guessMimeType(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
