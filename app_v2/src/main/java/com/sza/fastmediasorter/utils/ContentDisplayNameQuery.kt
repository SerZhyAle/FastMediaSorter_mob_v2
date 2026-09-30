package com.sza.fastmediasorter.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The [OpenableColumns.DISPLAY_NAME] of [uri], or null when the provider has none or refuses.
 *
 * Always runs on [ioDispatcher]: an external provider (a cloud DocumentsProvider) may block the
 * query for seconds, which on the UI thread is an ANR.
 */
suspend fun ContentResolver.queryDisplayName(
    uri: Uri,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): String? = withContext(ioDispatcher) {
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.onFailure { error ->
        Timber.w(error, "Display name query failed")
    }.getOrNull()
}

/**
 * The display name of the SAF tree [uri], or null when the provider has none or refuses.
 *
 * [DocumentFile.fromTreeUri] followed by `name` is a provider round-trip per call, so it runs on
 * [ioDispatcher] for the same reason as [queryDisplayName].
 */
suspend fun Context.queryTreeDisplayName(
    uri: Uri,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): String? = withContext(ioDispatcher) {
    runCatching { DocumentFile.fromTreeUri(this@queryTreeDisplayName, uri)?.name }
        .onFailure { error -> Timber.w(error, "Tree display name query failed") }
        .getOrNull()
}
