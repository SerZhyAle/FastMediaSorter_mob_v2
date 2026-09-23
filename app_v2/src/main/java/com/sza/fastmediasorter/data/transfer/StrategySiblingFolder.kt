package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import java.io.File
import java.io.IOException

/**
 * S3408: a network share directory (SMB, SFTP, FTP) as a [SiblingFolder], over the protocol's own
 * [FileOperationStrategy]. An address is the child's full protocol path; a rename is the strategy's
 * same-server move, which each of the three performs server-side.
 */
class StrategySiblingFolder(
    private val folderPath: String,
    private val strategy: FileOperationStrategy,
) : SiblingFolder {

    override suspend fun contains(name: String): Boolean = strategy.exists(childPath(name)).orIoFailure()

    override suspend fun write(source: File, name: String): String {
        val target = childPath(name)
        val written = strategy.copyFile(source.absolutePath, target, overwrite = false)
        if (written.isFailure) {
            // An upload cut short can leave a partial file under the name, and nobody else knows it.
            strategy.deleteFile(target)
        }
        return written.orIoFailure()
    }

    override suspend fun read(address: String, target: File) {
        strategy.copyFile(address, target.absolutePath, overwrite = true).orIoFailure()
    }

    override suspend fun rename(address: String, newName: String): String {
        val renamed = childPath(newName)
        strategy.moveFile(address, renamed).orIoFailure()
        return renamed
    }

    override suspend fun delete(address: String) {
        strategy.deleteFile(address).orIoFailure()
    }

    private fun childPath(name: String): String = folderPath.trimEnd('/') + "/" + name

    private fun <T> Result<T>.orIoFailure(): T =
        getOrElse { cause -> throw cause as? IOException ?: IOException("the remote folder refused", cause) }
}
