package com.sza.fastmediasorter.core.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.sza.fastmediasorter.data.repository.StorageVolumeSource
import com.sza.fastmediasorter.di.StorageVolumeEntryPoint
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

/**
 * Utility for resolving Android document-tree URIs (from ACTION_OPEN_DOCUMENT_TREE)
 * to real filesystem paths understood by java.io.File.
 *
 * Supports primary external storage and SD cards. S1378: volume lookup is delegated to
 * [StorageVolumeSource] - this object no longer talks to the platform storage service itself.
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
            // Not a tree URI - try direct document
            try { DocumentsContract.getDocumentId(uri) } catch (e2: Exception) { null }
        } ?: return null

        val colonIdx = docId.indexOf(':')
        if (colonIdx < 0) {
            // Downloads provider root: docId = "downloads" (no colon separator)
            return if (docId.equals("downloads", ignoreCase = true)) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            } else {
                Timber.tag(TAG).w("Cannot resolve docId without volume separator: '$docId'")
                null
            }
        }

        val volume  = docId.substring(0, colonIdx)
        val subPath = docId.substring(colonIdx + 1)

        return if (volume.equals("primary", ignoreCase = true)) {
            // Primary external storage
            val base = Environment.getExternalStorageDirectory().absolutePath
            if (subPath.isEmpty()) base else "$base/$subPath"
        } else {
            // SD card / removable storage - the registry owns the mount-point lookup.
            val mountPath = volumeSource(context)?.mountPathFor(volume)

            if (mountPath != null) {
                if (subPath.isEmpty()) mountPath else "$mountPath/$subPath"
            } else {
                Timber.tag(TAG).w("Cannot resolve SD card volume '$volume', falling back to null")
                null
            }
        }
    }

    /**
     * The registry, reached through Hilt because this resolver is an object.
     *
     * Returns null when the graph is not up yet - the caller then reports an unresolvable path
     * instead of crashing, which is the same outcome the old inline lookup produced on failure.
     */
    private fun volumeSource(context: Context): StorageVolumeSource? =
        try {
            EntryPointAccessors
                .fromApplication(context.applicationContext, StorageVolumeEntryPoint::class.java)
                .storageVolumeSource()
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).w(e, "Storage volume registry unavailable")
            null
        }
}
