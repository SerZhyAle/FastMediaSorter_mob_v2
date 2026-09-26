package com.sza.fastmediasorter.utils

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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
