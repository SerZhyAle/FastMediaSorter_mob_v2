package com.sza.fastmediasorter.data.remote.sftp.server

import java.io.File
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** Test double of [SftpServerNode] over a real directory, standing in for a SAF tree. */
class FileServerNode(private val file: File) : SftpServerNode {

    override val name: String get() = file.name
    override val isDirectory: Boolean get() = file.isDirectory
    override val length: Long get() = file.length()
    override val lastModified: Long get() = file.lastModified()
    override val canWrite: Boolean get() = file.canWrite()

    override fun listChildren(): List<SftpServerNode> =
        file.listFiles().orEmpty().sortedBy(File::getName).map(::FileServerNode)

    override fun findChild(name: String): SftpServerNode? =
        File(file, name).takeIf { it.exists() && it.parentFile == file }?.let(::FileServerNode)

    override fun createDirectory(name: String): SftpServerNode? =
        File(file, name).takeIf(File::mkdir)?.let(::FileServerNode)

    override fun createFile(name: String): SftpServerNode? =
        File(file, name).takeIf(File::createNewFile)?.let(::FileServerNode)

    override fun delete(): Boolean = file.deleteRecursively()

    override fun renameTo(newName: String): SftpServerNode? {
        val target = File(file.parentFile, newName)
        return if (file.renameTo(target)) FileServerNode(target) else null
    }

    override fun moveTo(newParent: SftpServerNode): SftpServerNode? {
        val parentDir = (newParent as FileServerNode).file
        val target = File(parentDir, file.name)
        return if (file.renameTo(target)) FileServerNode(target) else null
    }

    override fun openChannel(mode: SftpServerNode.ChannelMode): SeekableByteChannel {
        val options = when (mode) {
            SftpServerNode.ChannelMode.READ -> setOf(StandardOpenOption.READ)
            SftpServerNode.ChannelMode.WRITE -> setOf(StandardOpenOption.WRITE)
            SftpServerNode.ChannelMode.TRUNCATE ->
                setOf(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
            SftpServerNode.ChannelMode.APPEND -> setOf(StandardOpenOption.WRITE, StandardOpenOption.APPEND)
        }
        return Files.newByteChannel(file.toPath(), options)
    }
}
