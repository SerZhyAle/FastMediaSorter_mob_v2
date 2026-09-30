package com.sza.fastmediasorter.data.remote.sftp.server

import android.os.Build
import androidx.annotation.RequiresApi
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

/**
 * Maps a virtual SFTP path onto the user-picked SAF trees - the server's chroot.
 *
 * The namespace is always the same shape, whatever the number of picked trees: `/` is a read-only
 * virtual directory whose entries are the trees, each under its display name (a clash gets a
 * ` (2)`, ` (3)` suffix in pick order). One shape means a pairing code and a client bookmark stay
 * valid when the user adds a second folder later.
 *
 * Every path the server touches goes through [resolve]. A path that climbs above `/` is refused with
 * [AccessDeniedException] rather than clamped to the root: a client asking for `/../x` is either
 * confused or probing, and neither deserves a silent answer about some other directory.
 */
@RequiresApi(Build.VERSION_CODES.O)
class SafPathResolver(roots: List<SftpServerNode>) {

    data class Mount(val name: String, val node: SftpServerNode)

    sealed interface Target {
        /** `/` itself: lists the mounts, accepts no writes. */
        object VirtualRoot : Target

        data class Existing(val node: SftpServerNode, val isMountRoot: Boolean) : Target

        /** The parent exists and is a directory; [name] is not in it yet. */
        data class Absent(val parent: SftpServerNode, val name: String) : Target
    }

    val mounts: List<Mount> = mountNames(roots.map(SftpServerNode::name)).zip(roots, ::Mount)

    /**
     * Resolves [path] to a [Target]. Throws [AccessDeniedException] for a path escaping the chroot
     * and [NoSuchFileException] when an intermediate directory is missing or is a file.
     */
    fun resolve(path: String): Target {
        val segments = normalize(path)
        if (segments.isEmpty()) return Target.VirtualRoot
        val mount = mounts.firstOrNull { it.name == segments.first() } ?: throw NoSuchFileException(path)
        var current = mount.node
        for (index in 1 until segments.lastIndex) {
            current = current.findChild(segments[index])
                ?.takeIf(SftpServerNode::isDirectory)
                ?: throw NoSuchFileException(path)
        }
        return resolveLeaf(mount, current, segments)
    }

    private fun resolveLeaf(mount: Mount, parent: SftpServerNode, segments: List<String>): Target {
        if (segments.size == 1) return Target.Existing(mount.node, isMountRoot = true)
        val leafName = segments.last()
        val leaf = parent.findChild(leafName)
        return if (leaf != null) Target.Existing(leaf, isMountRoot = false) else Target.Absent(parent, leafName)
    }

    companion object {
        private const val SEPARATOR = '/'
        private const val CURRENT = "."
        private const val PARENT = ".."

        /**
         * Splits [path] into canonical segments. Empty and `.` segments vanish, `..` removes the
         * previous one, and a `..` with nothing left to remove is an escape. Percent sequences are
         * NOT decoded: SFTP paths are raw strings, so `%2e%2e` is a file literally named that.
         */
        fun normalize(path: String): List<String> {
            if (path.indexOf(NUL) >= 0) throw AccessDeniedException(path, null, "NUL in path")
            val result = ArrayDeque<String>()
            for (segment in path.split(SEPARATOR)) {
                when (segment) {
                    "", CURRENT -> Unit
                    PARENT -> if (result.isEmpty()) {
                        throw AccessDeniedException(path, null, "path escapes the server root")
                    } else {
                        result.removeLast()
                    }
                    else -> result.addLast(segment)
                }
            }
            return result.toList()
        }

        /** Display names for the mounts, unique in pick order; a blank name becomes `folder`. */
        fun mountNames(names: List<String>): List<String> {
            val taken = mutableSetOf<String>()
            return names.map { raw ->
                val base = raw.replace(SEPARATOR, '_').ifBlank { FALLBACK_MOUNT_NAME }
                var candidate = base
                var suffix = FIRST_DUPLICATE_SUFFIX
                while (!taken.add(candidate)) {
                    candidate = "$base ($suffix)"
                    suffix++
                }
                candidate
            }
        }

        private const val NUL = '\u0000'
        private const val FALLBACK_MOUNT_NAME = "folder"
        private const val FIRST_DUPLICATE_SUFFIX = 2
    }
}
