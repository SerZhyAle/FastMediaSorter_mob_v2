package com.sza.fastmediasorter.data.remote.sftp.server

import android.os.Build
import androidx.annotation.RequiresApi
import org.apache.sshd.common.file.FileSystemFactory
import org.apache.sshd.common.file.util.BaseFileSystem
import org.apache.sshd.common.file.util.BasePath
import org.apache.sshd.common.session.SessionContext
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.attribute.UserPrincipalNotFoundException

/**
 * MINA SSHD [FileSystemFactory] serving the user-picked SAF trees.
 *
 * SSHD 2.x has no file-system-view abstraction of its own: the SFTP subsystem speaks
 * `java.nio.file`. So each session gets a small NIO file system - [SftpSafFileSystem] with
 * [SftpSafFileSystemProvider] behind it - whose every path is resolved by [SafPathResolver], which is
 * what enforces the chroot.
 *
 * The roots are read per session through [rootsSupplier], so a folder added or removed in settings
 * applies to the next login without restarting the server.
 *
 * Two SAF limits are accepted rather than hidden (strategic spec section 3): a listing costs one
 * provider query per child, so very large folders list slowly; and a provider that serves a pipe
 * instead of a file cannot seek, so resuming a transfer at an offset fails on it.
 */
@RequiresApi(Build.VERSION_CODES.O)
class SftpSafFileSystemFactory(
    private val rootsSupplier: () -> List<SftpServerNode>,
) : FileSystemFactory {

    // Null lets the SFTP subsystem start in the file system's own default directory, `/`.
    override fun getUserHomeDir(session: SessionContext?): Path? = null

    override fun createFileSystem(session: SessionContext?): FileSystem =
        SftpSafFileSystem(SftpSafFileSystemProvider(SafPathResolver(rootsSupplier())))
}

@RequiresApi(Build.VERSION_CODES.O)
class SftpSafFileSystem(
    provider: SftpSafFileSystemProvider,
) : BaseFileSystem<SftpSafPath>(provider) {

    @Volatile
    private var open = true

    override fun create(root: String?, names: List<String>): SftpSafPath = SftpSafPath(this, root, names)

    override fun close() {
        open = false
    }

    override fun isOpen(): Boolean = open

    override fun supportedFileAttributeViews(): Set<String> = SUPPORTED_VIEWS

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService = PrincipalLookup

    private object PrincipalLookup : UserPrincipalLookupService() {
        override fun lookupPrincipalByName(name: String): UserPrincipal =
            if (name == SafPrincipal.OWNER.name) SafPrincipal.OWNER else throw UserPrincipalNotFoundException(name)

        override fun lookupPrincipalByGroupName(group: String): GroupPrincipal =
            if (group == SafPrincipal.OWNER.name) SafPrincipal.OWNER else throw UserPrincipalNotFoundException(group)
    }

    companion object {
        const val VIEW_BASIC = "basic"
        const val VIEW_POSIX = "posix"
        const val VIEW_OWNER = "owner"
        val SUPPORTED_VIEWS: Set<String> = setOf(VIEW_BASIC, VIEW_POSIX, VIEW_OWNER)
    }
}

/**
 * A path in [SftpSafFileSystem]. [cachedNode] is set on the entries a directory listing produces, so
 * the attribute read the SFTP subsystem makes for every listed entry does not walk the tree again -
 * on SAF each step of that walk is a provider query.
 */
@RequiresApi(Build.VERSION_CODES.O)
class SftpSafPath(
    fileSystem: SftpSafFileSystem,
    root: String?,
    names: List<String>,
) : BasePath<SftpSafPath, SftpSafFileSystem>(fileSystem, root, names) {

    @Volatile
    var cachedNode: SftpServerNode? = null
        private set

    fun withNode(node: SftpServerNode): SftpSafPath = apply { cachedNode = node }

    override fun toRealPath(vararg options: LinkOption): Path = toAbsolutePath().normalize()
}

/** The single owner and group every served entry reports; SAF has no ownership to expose. */
class SafPrincipal private constructor(private val principalName: String) : GroupPrincipal {
    override fun getName(): String = principalName

    override fun toString(): String = principalName

    companion object {
        val OWNER = SafPrincipal("fms")
    }
}

/** Read-only attributes of one served entry, synthesised for the `basic`, `posix` and `owner` views. */
@RequiresApi(Build.VERSION_CODES.O)
class SafFileAttributes private constructor(
    private val directory: Boolean,
    private val size: Long,
    private val modified: FileTime,
    private val writable: Boolean,
) : PosixFileAttributes {

    override fun lastModifiedTime(): FileTime = modified
    override fun lastAccessTime(): FileTime = modified
    override fun creationTime(): FileTime = modified
    override fun isRegularFile(): Boolean = !directory
    override fun isDirectory(): Boolean = directory
    override fun isSymbolicLink(): Boolean = false
    override fun isOther(): Boolean = false
    override fun size(): Long = size
    override fun fileKey(): Any? = null
    override fun owner(): UserPrincipal = SafPrincipal.OWNER
    override fun group(): GroupPrincipal = SafPrincipal.OWNER

    override fun permissions(): Set<PosixFilePermission> = buildSet {
        add(PosixFilePermission.OWNER_READ)
        add(PosixFilePermission.GROUP_READ)
        add(PosixFilePermission.OTHERS_READ)
        if (writable) add(PosixFilePermission.OWNER_WRITE)
        if (directory) {
            add(PosixFilePermission.OWNER_EXECUTE)
            add(PosixFilePermission.GROUP_EXECUTE)
            add(PosixFilePermission.OTHERS_EXECUTE)
        }
    }

    /** The attribute map for [view], restricted to [names] unless it is `*`. */
    fun toMap(view: String, names: Set<String>): Map<String, Any?> {
        val all = buildMap<String, Any?> {
            if (view != SftpSafFileSystem.VIEW_OWNER) {
                put("size", size())
                put("lastModifiedTime", lastModifiedTime())
                put("lastAccessTime", lastAccessTime())
                put("creationTime", creationTime())
                put("isRegularFile", isRegularFile)
                put("isDirectory", isDirectory)
                put("isSymbolicLink", isSymbolicLink)
                put("isOther", isOther)
                put("fileKey", fileKey())
            }
            if (view != SftpSafFileSystem.VIEW_BASIC) put("owner", owner())
            if (view == SftpSafFileSystem.VIEW_POSIX) {
                put("group", group())
                put("permissions", permissions())
            }
        }
        return if (ALL_ATTRIBUTES in names) all else all.filterKeys(names::contains)
    }

    companion object {
        const val ALL_ATTRIBUTES = "*"

        fun of(node: SftpServerNode): SafFileAttributes = SafFileAttributes(
            directory = node.isDirectory,
            size = if (node.isDirectory) 0L else node.length,
            modified = FileTime.fromMillis(node.lastModified),
            writable = node.canWrite,
        )

        /** `/` lists the mounts and accepts no writes. */
        val VIRTUAL_ROOT = SafFileAttributes(
            directory = true,
            size = 0L,
            modified = FileTime.fromMillis(0L),
            writable = false,
        )
    }
}
