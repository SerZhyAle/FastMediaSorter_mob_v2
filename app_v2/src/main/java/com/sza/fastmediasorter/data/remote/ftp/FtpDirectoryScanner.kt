@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.remote.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import timber.log.Timber
import java.net.SocketTimeoutException

/**
 * FTP directory traversal helpers for FtpClient. All three variants share the same
 * passive→active mode fallback when `listFiles` times out (some servers have flaky NAT
 * for passive PORT replies).
 *
 * Stateless beyond the supplied [FTPClient]; can be called concurrently against different
 * client instances.
 *
 * Extracted to keep FtpClient below the 1000-line cap.
 */
object FtpDirectoryScanner {

    /** Pagination cursor for [listFilesWithMetadataRecursivePaged]. */
    data class MetadataPagingState(
        val offset: Int,
        val limit: Int,
        var skipped: Int = 0,
        var collected: Int = 0
    )

    /** Single-level scan: filters out `.` / `..` and directories, returns only files. */
    fun listFilesWithMetadataSingleLevel(
        client: FTPClient,
        remotePath: String,
        results: MutableList<FTPFile>
    ) {
        val ftpFiles = listWithPassiveActiveFallback(client, remotePath, "single-level")
        ftpFiles.forEach { ftpFile ->
            if (ftpFile.name != "." && ftpFile.name != ".." && ftpFile.isFile) {
                results.add(ftpFile)
            }
        }
    }

    /** Recursive scan: descends into subdirectories, accumulating only file entries. */
    fun listFilesWithMetadataRecursive(
        client: FTPClient,
        remotePath: String,
        results: MutableList<FTPFile>
    ) {
        val ftpFiles = listWithPassiveActiveFallback(client, remotePath, "recursive")
        ftpFiles.forEach { ftpFile ->
            if (ftpFile.name == "." || ftpFile.name == "..") return@forEach
            val subPath = joinPath(remotePath, ftpFile.name)
            if (ftpFile.isDirectory) {
                listFilesWithMetadataRecursive(client, subPath, results)
            } else if (ftpFile.isFile) {
                results.add(ftpFile)
            }
        }
    }

    /**
     * Recursive scan with offset/limit pagination. Stops descending as soon as
     * [paging].collected reaches [paging].limit; skips the first [paging].offset files.
     */
    fun listFilesWithMetadataRecursivePaged(
        client: FTPClient,
        remotePath: String,
        results: MutableList<FTPFile>,
        paging: MetadataPagingState
    ) {
        if (paging.collected >= paging.limit) return

        val ftpFiles = listWithPassiveActiveFallback(client, remotePath, "paged")

        ftpFiles.forEach { ftpFile ->
            if (paging.collected >= paging.limit) return
            if (ftpFile.name == "." || ftpFile.name == "..") return@forEach

            val subPath = joinPath(remotePath, ftpFile.name)

            if (ftpFile.isDirectory) {
                listFilesWithMetadataRecursivePaged(client, subPath, results, paging)
            } else if (ftpFile.isFile) {
                if (paging.skipped < paging.offset) {
                    paging.skipped++
                } else {
                    results.add(ftpFile)
                    paging.collected++
                }
            }
        }
    }

    /**
     * Try `listFiles` in passive mode; on [SocketTimeoutException], switch to active mode,
     * retry, and switch back to passive (so subsequent operations on the same client work).
     */
    private fun listWithPassiveActiveFallback(
        client: FTPClient,
        remotePath: String,
        scope: String
    ): Array<FTPFile> = try {
        Timber.d("FTP listing files ($scope) in passive mode: $remotePath")
        client.listFiles(remotePath)
    } catch (e: SocketTimeoutException) {
        Timber.w(e, "FTP passive mode timeout ($scope), switching to active mode")
        client.enterLocalActiveMode()
        Timber.d("FTP retrying listFiles ($scope) in active mode: $remotePath")
        try {
            client.listFiles(remotePath)
        } finally {
            try {
                client.enterLocalPassiveMode()
                Timber.d("FTP switched back to passive mode")
            } catch (ignored: Exception) {
                Timber.w(ignored, "Failed to switch back to passive mode")
            }
        }
    }

    private fun joinPath(parent: String, child: String): String =
        if (parent.endsWith("/")) parent + child else "$parent/$child"
}
