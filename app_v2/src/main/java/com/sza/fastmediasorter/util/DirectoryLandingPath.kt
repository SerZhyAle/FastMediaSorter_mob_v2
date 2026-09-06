package com.sza.fastmediasorter.util

/**
 * Where a directory tree at [sourcePath] lands when transferred into [destParentPath].
 *
 * S1326: the undo record has to name the landing path, and it must be the same string the transfer
 * itself produced - a record pointing one character away from the real tree fails silently at replay
 * rather than at write time. The rule lived inline in both `executeCopyDirectory` and
 * `executeMoveDirectory`; it is defined here once so a reader cannot re-derive a fourth variant.
 *
 * It sits in `util` rather than beside the transfer handler because all three callers span layers -
 * the data-layer handler, the worker and the Browse UI manager that writes the undo record - and a
 * UI file may not import from `data` (S2103).
 */
internal fun directoryLandingPath(sourcePath: String, destParentPath: String): String {
    val dirName = sourcePath.trimEnd('/').substringAfterLast('/')
    return if (destParentPath.endsWith('/')) "$destParentPath$dirName" else "$destParentPath/$dirName"
}
