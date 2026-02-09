package com.sza.fastmediasorter.data.transfer

import android.net.Uri

/**
 * Interface for basic file access operations that differ by protocol.
 * Used by UniversalFileOperationHandler for pre-checks and delete (move) operations.
 */
interface FileAccess {
    fun supports(scheme: String?): Boolean
    suspend fun exists(uri: Uri): Boolean
    suspend fun delete(uri: Uri): Boolean
}
