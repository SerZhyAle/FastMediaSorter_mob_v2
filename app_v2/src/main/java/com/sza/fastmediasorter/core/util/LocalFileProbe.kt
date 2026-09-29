package com.sza.fastmediasorter.core.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Filesystem probes that always leave the caller's dispatcher. UI helpers call these instead of
 * stat-ing or canonicalizing on the main thread; the dispatcher is a parameter so a test can
 * substitute its own.
 */
object LocalFileProbe {

    data class Stat(val length: Long, val lastModified: Long)

    suspend fun stat(file: File, dispatcher: CoroutineDispatcher = Dispatchers.IO): Stat? =
        withContext(dispatcher) {
            if (file.exists()) Stat(file.length(), file.lastModified()) else null
        }

    suspend fun canonicalPaths(
        paths: Collection<String>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Map<String, String> = withContext(dispatcher) { paths.associateWith { canonicalOrSelf(it) } }

    /**
     * Resolves symlinked mount aliases (`/sdcard` vs `/storage/emulated/0`) so two spellings of one
     * file compare equal. Protocol URIs are returned unchanged: File would resolve them against the
     * working directory.
     */
    fun canonicalOrSelf(path: String): String {
        if (PROTOCOL_PREFIXES.any { path.startsWith(it) }) return path
        return try {
            File(path).canonicalPath
        } catch (e: IOException) {
            Timber.w(e, "LocalFileProbe: canonicalPath failed, comparing the raw path")
            path
        } catch (e: SecurityException) {
            Timber.w(e, "LocalFileProbe: canonicalPath denied, comparing the raw path")
            path
        }
    }

    private val PROTOCOL_PREFIXES = listOf("content://", "smb://", "sftp://", "ftp://")
}
