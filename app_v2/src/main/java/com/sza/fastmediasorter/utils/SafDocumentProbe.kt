package com.sza.fastmediasorter.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main-safe SAF single-document probes (S3790).
 *
 * Every [DocumentFile] call here is a binder IPC to the owning provider, so both probes suspend
 * on [Dispatchers.IO] and callers stay on the main dispatcher. Same layering as
 * [ContentDisplayNameQuery] (S3750): the UI layer asks a question, this class owns the
 * cross-process call. Errors are the caller's policy, so no exception is swallowed here.
 */
object SafDocumentProbe {

    /** True when the provider still serves the single document behind [uri]. */
    suspend fun exists(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        DocumentFile.fromSingleUri(context, uri)?.exists() == true
    }

    /** True when the single document behind [uri] is readable by this app. */
    suspend fun canRead(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        DocumentFile.fromSingleUri(context, uri)?.canRead() ?: false
    }
}
