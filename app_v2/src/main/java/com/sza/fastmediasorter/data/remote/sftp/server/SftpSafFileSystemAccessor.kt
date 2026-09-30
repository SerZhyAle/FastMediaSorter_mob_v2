package com.sza.fastmediasorter.data.remote.sftp.server

import android.os.Build
import androidx.annotation.RequiresApi
import org.apache.sshd.sftp.server.DirectoryHandle
import org.apache.sshd.sftp.server.FileHandle
import org.apache.sshd.sftp.server.SftpFileSystemAccessor
import org.apache.sshd.sftp.server.SftpSubsystemProxy
import timber.log.Timber
import java.nio.channels.Channel
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.SeekableByteChannel
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NotLinkException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal
import java.security.Principal

/**
 * Adapts MINA's SFTP subsystem to [SftpSafFileSystem].
 *
 * The default accessor opens files with `FileChannel.open`, which a custom NIO provider cannot serve,
 * and it passes attribute changes straight through. Here files open through the provider's byte
 * channel, and attribute changes - timestamps, permissions, ownership, which SAF does not have - are
 * accepted and ignored: WinSCP and FileZilla set the modification time after every upload and would
 * otherwise report each upload as failed.
 */
@RequiresApi(Build.VERSION_CODES.O)
object SftpSafFileSystemAccessor : SftpFileSystemAccessor {

    override fun openFile(
        subsystem: SftpSubsystemProxy?,
        fileHandle: FileHandle?,
        file: Path,
        handle: String?,
        options: Set<OpenOption>,
        vararg attrs: FileAttribute<*>,
    ): SeekableByteChannel = file.fileSystem.provider().newByteChannel(file, options, *attrs)

    override fun openDirectory(
        subsystem: SftpSubsystemProxy?,
        dirHandle: DirectoryHandle?,
        dir: Path,
        handle: String?,
        vararg linkOptions: LinkOption,
    ): DirectoryStream<Path> = Files.newDirectoryStream(dir)

    override fun tryLock(
        subsystem: SftpSubsystemProxy?,
        fileHandle: FileHandle?,
        file: Path?,
        handle: String?,
        channel: Channel?,
        position: Long,
        size: Long,
        shared: Boolean,
    ): FileLock = throw UnsupportedOperationException("Byte-range locks are not available on shared folders")

    override fun syncFileData(
        subsystem: SftpSubsystemProxy?,
        fileHandle: FileHandle?,
        file: Path?,
        handle: String?,
        channel: Channel?,
    ) {
        (channel as? FileChannel)?.force(false)
    }

    override fun setFileAttribute(
        subsystem: SftpSubsystemProxy?,
        file: Path?,
        view: String?,
        attribute: String?,
        value: Any?,
        vararg options: LinkOption,
    ) {
        Timber.v("SftpSafFileSystemAccessor: ignoring attribute %s:%s on %s", view, attribute, file)
    }

    override fun setFilePermissions(
        subsystem: SftpSubsystemProxy?,
        file: Path?,
        perms: Set<PosixFilePermission>?,
        vararg options: LinkOption,
    ) = Unit

    override fun setFileOwner(
        subsystem: SftpSubsystemProxy?,
        file: Path?,
        value: Principal?,
        vararg options: LinkOption,
    ) = Unit

    override fun setGroupOwner(
        subsystem: SftpSubsystemProxy?,
        file: Path?,
        value: Principal?,
        vararg options: LinkOption,
    ) = Unit

    override fun setFileAccessControl(
        subsystem: SftpSubsystemProxy?,
        file: Path?,
        acl: List<AclEntry>?,
        vararg options: LinkOption,
    ) = Unit

    override fun resolveFileOwner(subsystem: SftpSubsystemProxy?, file: Path?, name: UserPrincipal?): UserPrincipal =
        SafPrincipal.OWNER

    override fun resolveGroupOwner(subsystem: SftpSubsystemProxy?, file: Path?, name: GroupPrincipal?): GroupPrincipal =
        SafPrincipal.OWNER

    override fun createLink(subsystem: SftpSubsystemProxy?, link: Path?, existing: Path?, symLink: Boolean) {
        throw UnsupportedOperationException("Links cannot be created on shared folders")
    }

    override fun resolveLinkTarget(subsystem: SftpSubsystemProxy?, link: Path): String =
        throw NotLinkException(link.toString())

    override fun copyFile(subsystem: SftpSubsystemProxy?, src: Path, dst: Path, opts: Collection<CopyOption>) {
        // REPLACE_EXISTING is the only copy option the SAF provider honours.
        val provider = src.fileSystem.provider()
        if (StandardCopyOption.REPLACE_EXISTING in opts) {
            provider.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
        } else {
            provider.copy(src, dst)
        }
    }
}
