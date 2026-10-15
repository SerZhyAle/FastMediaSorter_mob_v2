package com.sza.fastmediasorter.data.remote.sftp

import androidx.annotation.WorkerThread
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import timber.log.Timber
import java.util.Vector

/**
 * The two directory walks behind [SftpClient.listFiles]. They are plain functions of a connected channel, so they
 * live apart from the client, which would otherwise outgrow the size one class may have.
 *
 * Both walks issue blocking `ls` calls and are only entered from the connection pool's confined `withConnection`
 * lambda. The annotation states that for lint, which judges confinement inside one file and cannot see the caller.
 */
internal object SftpListingWalker {

    // List files in single directory level (non-recursive)
    @WorkerThread
    fun listFilesSingleLevel(
        channel: ChannelSftp,
        remotePath: String,
        results: MutableList<SftpFileListing>,
        includeDirectories: Boolean = false
    ) {
        val entries = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>

        entries.forEach { entry ->
            if (entry.filename != "." && entry.filename != "..") {
                val fullPath = if (remotePath.endsWith("/")) {
                    remotePath + entry.filename
                } else {
                    "$remotePath/${entry.filename}"
                }

                if (entry.attrs.isDir && !includeDirectories) {
                    // Skip directories in scan mode (caller does not want them)
                    return@forEach
                }

                results.add(
                    SftpFileListing(
                        path = fullPath,
                        size = entry.attrs.size,
                        isDirectory = entry.attrs.isDir,
                        modifiedDate = entry.attrs.mTime.toLong() * 1000L
                    )
                )
            }
        }
    }

    // List files recursively in all subdirectories
    @WorkerThread
    fun listFilesRecursive(
        channel: ChannelSftp,
        remotePath: String,
        results: MutableList<SftpFileListing>,
        skipped: MutableList<String>,
        isRoot: Boolean = true
    ) {
        val entries = try {
            channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
        } catch (e: SftpException) {
            // S2315: one unreadable subdirectory used to abort the whole walk, so a share with a
            // single ACL-broken folder reported zero files and read as an empty resource. The root
            // is different: failing there means the resource itself is gone, which the caller must
            // see as an error rather than as an empty folder.
            if (isRoot) throw e
            skipped.add(remotePath)
            Timber.w("SFTP: skipping unreadable directory '$remotePath' - ${e.message}")
            return
        }

        entries.forEach { entry ->
            if (entry.filename != "." && entry.filename != "..") {
                val fullPath = if (remotePath.endsWith("/")) {
                    remotePath + entry.filename
                } else {
                    "$remotePath/${entry.filename}"
                }

                if (entry.attrs.isDir) {
                    // Recursively scan subdirectory - directory itself is not added to results
                    listFilesRecursive(channel, fullPath, results, skipped, isRoot = false)
                } else {
                    results.add(
                        SftpFileListing(
                            path = fullPath,
                            size = entry.attrs.size,
                            isDirectory = false,
                            modifiedDate = entry.attrs.mTime.toLong() * 1000L
                        )
                    )
                }
            }
        }
    }
}
