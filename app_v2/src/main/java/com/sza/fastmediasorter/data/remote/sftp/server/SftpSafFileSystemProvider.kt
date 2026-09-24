package com.sza.fastmediasorter.data.remote.sftp.server

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessDeniedException
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.DirectoryStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.spi.FileSystemProvider

/**
 * The NIO provider behind [SftpSafFileSystem]. Every operation resolves its path through
 * [SafPathResolver] first, so no call can reach storage outside the picked trees.
 *
 * `/` and the mount roots are structure, not content: they cannot be deleted, renamed or written to.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Suppress("TooManyFunctions")
class SftpSafFileSystemProvider(
    private val resolver: SafPathResolver,
) : FileSystemProvider() {

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem =
        throw UnsupportedOperationException("SAF file systems are created per SFTP session")

    override fun getFileSystem(uri: URI): FileSystem =
        throw UnsupportedOperationException("SAF file systems are not addressable by URI")

    override fun getPath(uri: URI): Path = throw UnsupportedOperationException("SAF paths are not addressable by URI")

    override fun newByteChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attrs: FileAttribute<*>,
    ): SeekableByteChannel {
        val write = StandardOpenOption.WRITE in options || StandardOpenOption.APPEND in options
        val node = when (val target = resolve(path)) {
            SafPathResolver.Target.VirtualRoot -> throw FileSystemException(path.toString(), null, IS_A_DIRECTORY)
            is SafPathResolver.Target.Existing -> {
                if (StandardOpenOption.CREATE_NEW in options) throw FileAlreadyExistsException(path.toString())
                target.node
            }
            is SafPathResolver.Target.Absent -> createForWrite(path, target, write, options)
        }
        if (node.isDirectory) throw FileSystemException(path.toString(), null, IS_A_DIRECTORY)
        return node.openChannel(channelMode(write, options))
    }

    override fun newDirectoryStream(
        dir: Path,
        filter: DirectoryStream.Filter<in Path>,
    ): DirectoryStream<Path> {
        val base = dir as SftpSafPath
        val entries: List<Path> = when (val target = resolve(dir)) {
            SafPathResolver.Target.VirtualRoot -> resolver.mounts.map { base.resolve(it.name).withNode(it.node) }
            is SafPathResolver.Target.Existing -> {
                if (!target.node.isDirectory) throw NotDirectoryException(dir.toString())
                target.node.listChildren().map { base.resolve(it.name).withNode(it) }
            }
            is SafPathResolver.Target.Absent -> throw NoSuchFileException(dir.toString())
        }
        return ListDirectoryStream(entries.filter(filter::accept))
    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        when (val target = resolve(dir)) {
            is SafPathResolver.Target.Absent -> target.parent.createDirectory(target.name)
                ?: throw AccessDeniedException(dir.toString(), null, PROVIDER_REFUSED)
            else -> throw FileAlreadyExistsException(dir.toString())
        }
    }

    override fun delete(path: Path) {
        val node = existingContent(path)
        if (node.isDirectory && node.listChildren().isNotEmpty()) {
            // SAF deletes a directory recursively; SFTP rmdir must not.
            throw DirectoryNotEmptyException(path.toString())
        }
        if (!node.delete()) throw AccessDeniedException(path.toString(), null, PROVIDER_REFUSED)
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        val node = existingContent(source)
        if (node.isDirectory) throw FileSystemException(source.toString(), null, IS_A_DIRECTORY)
        val destination = prepareDestination(target, options)
        copyContent(node, destination.parent, destination.name, target)
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        val node = existingContent(source)
        val destination = prepareDestination(target, options)
        val sourceParent = parentPath(source)
        val sameParent = sourceParent == parentPath(target)
        val moved = if (sameParent) {
            node.renameTo(destination.name)
        } else {
            node.moveTo(destination.parent)
                ?.let { if (it.name == destination.name) it else it.renameTo(destination.name) }
        }
        if (moved != null) return
        if (node.isDirectory) throw AccessDeniedException(source.toString(), target.toString(), PROVIDER_REFUSED)
        // Cross-tree moves are not something every provider supports; a file still moves by copy.
        copyContent(node, destination.parent, destination.name, target)
        if (!node.delete()) throw AccessDeniedException(source.toString(), null, PROVIDER_REFUSED)
    }

    override fun isSameFile(path: Path, path2: Path): Boolean =
        path.toAbsolutePath().normalize() == path2.toAbsolutePath().normalize()

    override fun isHidden(path: Path): Boolean = path.fileName?.toString()?.startsWith(".") == true

    override fun getFileStore(path: Path): FileStore = throw UnsupportedOperationException("No file store for SAF")

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        val attributes = attributesOf(path)
        val denied = modes.any { mode ->
            when (mode) {
                AccessMode.WRITE -> PosixFilePermission.OWNER_WRITE !in attributes.permissions()
                AccessMode.EXECUTE -> !attributes.isDirectory
                else -> false
            }
        }
        if (denied) throw AccessDeniedException(path.toString())
    }

    override fun <V : FileAttributeView?> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption,
    ): V? {
        if (!type.isAssignableFrom(ReadOnlyAttributeView::class.java)) return null
        return type.cast(ReadOnlyAttributeView { attributesOf(path) })
    }

    override fun <A : BasicFileAttributes?> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption,
    ): A {
        if (!type.isAssignableFrom(PosixFileAttributes::class.java)) {
            throw UnsupportedOperationException("Attributes ${type.name} not supported")
        }
        return type.cast(attributesOf(path))
    }

    override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption): Map<String, Any?> {
        val separator = attributes.indexOf(':')
        val view = if (separator < 0) SftpSafFileSystem.VIEW_BASIC else attributes.substring(0, separator)
        if (view !in SftpSafFileSystem.SUPPORTED_VIEWS) {
            throw UnsupportedOperationException("Attribute view $view not supported")
        }
        val names = attributes.substring(separator + 1).split(',').toSet()
        return attributesOf(path).toMap(view, names)
    }

    override fun setAttribute(path: Path, attribute: String, value: Any?, vararg options: LinkOption) {
        throw UnsupportedOperationException("SAF entries accept no attribute changes")
    }

    private fun resolve(path: Path): SafPathResolver.Target = resolver.resolve(path.toAbsolutePath().toString())

    private fun attributesOf(path: Path): SafFileAttributes {
        (path as? SftpSafPath)?.cachedNode?.let { return SafFileAttributes.of(it) }
        return when (val target = resolve(path)) {
            SafPathResolver.Target.VirtualRoot -> SafFileAttributes.VIRTUAL_ROOT
            is SafPathResolver.Target.Existing -> SafFileAttributes.of(target.node)
            is SafPathResolver.Target.Absent -> throw NoSuchFileException(path.toString())
        }
    }

    /** An entry the client may delete, rename or copy: anything below a mount root. */
    private fun existingContent(path: Path): SftpServerNode = when (val target = resolve(path)) {
        is SafPathResolver.Target.Existing -> {
            if (target.isMountRoot) throw AccessDeniedException(path.toString(), null, STRUCTURE_ENTRY)
            target.node
        }
        SafPathResolver.Target.VirtualRoot -> throw AccessDeniedException(path.toString(), null, STRUCTURE_ENTRY)
        is SafPathResolver.Target.Absent -> throw NoSuchFileException(path.toString())
    }

    private fun prepareDestination(target: Path, options: Array<out CopyOption>): SafPathResolver.Target.Absent =
        when (val resolved = resolve(target)) {
            is SafPathResolver.Target.Absent -> resolved
            is SafPathResolver.Target.Existing -> {
                if (StandardCopyOption.REPLACE_EXISTING !in options || resolved.isMountRoot) {
                    throw FileAlreadyExistsException(target.toString())
                }
                delete(target)
                resolve(target) as? SafPathResolver.Target.Absent
                    ?: throw FileAlreadyExistsException(target.toString())
            }
            SafPathResolver.Target.VirtualRoot -> throw FileAlreadyExistsException(target.toString())
        }

    private fun createForWrite(
        path: Path,
        target: SafPathResolver.Target.Absent,
        write: Boolean,
        options: Set<OpenOption>,
    ): SftpServerNode {
        val mayCreate = StandardOpenOption.CREATE in options || StandardOpenOption.CREATE_NEW in options
        if (!write || !mayCreate) throw NoSuchFileException(path.toString())
        return target.parent.createFile(target.name)
            ?: throw AccessDeniedException(path.toString(), null, PROVIDER_REFUSED)
    }

    private fun copyContent(node: SftpServerNode, parent: SftpServerNode, name: String, target: Path) {
        val created = parent.createFile(name) ?: throw AccessDeniedException(target.toString(), null, PROVIDER_REFUSED)
        node.openChannel(SftpServerNode.ChannelMode.READ).use { input ->
            created.openChannel(SftpServerNode.ChannelMode.TRUNCATE).use { output ->
                val buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES)
                while (input.read(buffer) >= 0) {
                    buffer.flip()
                    while (buffer.hasRemaining()) output.write(buffer)
                    buffer.clear()
                }
            }
        }
    }

    private fun parentPath(path: Path): String = path.toAbsolutePath().normalize().parent?.toString().orEmpty()

    private fun channelMode(write: Boolean, options: Set<OpenOption>): SftpServerNode.ChannelMode = when {
        !write -> SftpServerNode.ChannelMode.READ
        StandardOpenOption.APPEND in options -> SftpServerNode.ChannelMode.APPEND
        StandardOpenOption.TRUNCATE_EXISTING in options -> SftpServerNode.ChannelMode.TRUNCATE
        else -> SftpServerNode.ChannelMode.WRITE
    }

    private class ListDirectoryStream(private val entries: List<Path>) : DirectoryStream<Path> {
        @Volatile
        private var consumed = false

        override fun iterator(): MutableIterator<Path> {
            check(!consumed) { "Directory stream iterated twice" }
            consumed = true
            return entries.toMutableList().iterator()
        }

        override fun close() = Unit
    }

    /** Timestamps and permissions do not exist on SAF, so every setter is refused. */
    private class ReadOnlyAttributeView(
        private val read: () -> SafFileAttributes,
    ) : BasicFileAttributeView {
        override fun name(): String = SftpSafFileSystem.VIEW_BASIC

        override fun readAttributes(): BasicFileAttributes = read()

        override fun setTimes(lastModifiedTime: FileTime?, lastAccessTime: FileTime?, createTime: FileTime?) {
            throw IOException("SAF entries keep their own timestamps")
        }
    }

    companion object {
        const val SCHEME = "fms-saf"
        private const val IS_A_DIRECTORY = "is a directory"
        private const val PROVIDER_REFUSED = "the storage provider refused the operation"
        private const val STRUCTURE_ENTRY = "the server root and its shared folders cannot be changed"
        private const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
