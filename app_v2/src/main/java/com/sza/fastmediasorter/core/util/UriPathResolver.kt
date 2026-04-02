package com.sza.fastmediasorter.core.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import timber.log.Timber

/**
 * Utility for resolving Android document-tree URIs (from ACTION_OPEN_DOCUMENT_TREE)
 * to real filesystem paths understood by java.io.File.
 *
 * Supports primary external storage and SD cards (via StorageManager).
 */
object UriPathResolver {

    private const val TAG = "UriPathResolver"

    /**
     * Resolve a document-tree URI to a filesystem path.
     *
     * @param context Application context
     * @param uri     URI returned by ACTION_OPEN_DOCUMENT_TREE
     * @return Absolute filesystem path, or null if resolution is not possible
     */
    fun getPath(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> resolveContentUri(context, uri)
                "file"    -> uri.path
                else      -> null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to resolve uri=$uri")
            null
        }
    }

    private fun resolveContentUri(context: Context, uri: Uri): String? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            // Not a tree URI — try direct document
            try { DocumentsContract.getDocumentId(uri) } catch (e2: Exception) { null }
        } ?: return null

        val colonIdx = docId.indexOf(':')
        if (colonIdx < 0) return null

        val volume  = docId.substring(0, colonIdx)
        val subPath = docId.substring(colonIdx + 1)

        return if (volume.equals("primary", ignoreCase = true)) {
            // Primary external storage
            val base = Environment.getExternalStorageDirectory().absolutePath
            if (subPath.isEmpty()) base else "$base/$subPath"
        } else {
            // SD card / removable storage — look up the mount point from StorageManager
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val mountPath = storageManager.storageVolumes
                .firstOrNull { it.uuid?.equals(volume, ignoreCase = true) == true }
                ?.let { volumePath(it) }

            if (mountPath != null) {
                if (subPath.isEmpty()) mountPath else "$mountPath/$subPath"
            } else {
                Timber.tag(TAG).w("Cannot resolve SD card volume '$volume', falling back to null")
                null
            }
        }
    }

    /**
     * Retrieve the mount path for a StorageVolume via reflection.
     * StorageVolume.getPath() is a hidden API but stable since API 24.
     */
    private fun volumePath(volume: android.os.storage.StorageVolume): String? {
        return try {
            val method = volume.javaClass.getMethod("getPath")
            method.invoke(volume) as? String
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Reflection failed for StorageVolume.getPath()")
            null
        }
    }
}
