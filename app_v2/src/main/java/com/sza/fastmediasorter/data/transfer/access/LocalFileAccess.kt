package com.sza.fastmediasorter.data.transfer.access

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.transfer.FileAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class LocalFileAccess @Inject constructor(
    @param:ApplicationContext private val context: Context
) : FileAccess {

    override fun supports(scheme: String?): Boolean {
        return scheme == "file" || scheme == null || scheme == "content"
    }

    override suspend fun exists(uri: Uri): Boolean {
        if (uri.scheme == "content") {
             // For content URIs, existence is tricky. We assume it exists if we can query it.
             return try {
                 context.contentResolver.query(uri, null, null, null, null)?.use {
                     it.moveToFirst()
                 } ?: false
             } catch (e: Exception) {
                 e.rethrowIfCancellation()
                 false
             }
        }
        val path = uri.path ?: return false
        return File(path).exists()
    }

    override suspend fun delete(uri: Uri): Boolean {
        if (uri.scheme == "content") {
            return try {
                // Use SafHelper which handles tree document URIs
                com.sza.fastmediasorter.utils.SafHelper.deleteContentUri(
                    context, uri.toString(), "LocalFileAccess.delete"
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                false
            }
        }
        val path = uri.path ?: return false
        val file = File(path)
        return if (file.exists()) {
            file.delete()
        } else {
            true // Already gone
        }
    }
}
