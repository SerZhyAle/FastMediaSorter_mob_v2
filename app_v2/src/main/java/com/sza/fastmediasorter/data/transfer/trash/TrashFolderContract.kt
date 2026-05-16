package com.sza.fastmediasorter.data.transfer.trash

/**
 * Single source of truth for trash-folder naming conventions used by soft-delete.
 *
 * Canonical on-disk layout for any LOCAL parent directory:
 *
 *   <parentPath>/.trash/<timestampMs>/<deletedFile>
 *   <parentPath>/.trash/<timestampMs>/metadata.json
 *
 * - [CONTAINER_NAME] is the literal name of the container directory ("`.trash`",
 *   no underscore) placed next to source files.
 * - Every batch of files deleted in one operation goes into its own snapshot
 *   subdirectory named after `System.currentTimeMillis()`.
 *
 * Legacy layout produced by earlier versions of the app:
 *
 *   <parentPath>/.trash_<timestampMs>/<deletedFile>
 *
 * Recognised by [isLegacyContainerDir] / [parseLegacyTimestamp] strictly for
 * one-time best-effort cleanup; new code never writes legacy paths.
 *
 * Producers, automatic cleaners, manual "clear trash" actions, and the restore
 * use-case MUST go through this object. String literals of "`.trash`" or
 * "`.trash_`" elsewhere in production code are a contract violation.
 */
object TrashFolderContract {

    /** Canonical container directory name placed next to source files. */
    const val CONTAINER_NAME = ".trash"

    /** Legacy single-level container prefix from prior versions; recognised for migration only. */
    private const val LEGACY_PREFIX = ".trash_"

    /**
     * Build the snapshot subdirectory path for one soft-delete batch.
     * Result: "<parentPath>/.trash/<timestampMs>".
     */
    fun buildSnapshotPath(parentPath: String, timestampMs: Long): String {
        return "$parentPath/$CONTAINER_NAME/$timestampMs"
    }

    /**
     * Build the container directory path for [parentPath].
     * Result: "<parentPath>/.trash".
     */
    fun buildContainerPath(parentPath: String): String {
        return "$parentPath/$CONTAINER_NAME"
    }

    /**
     * Return true when [name] equals the canonical container directory name.
     * Strictly literal — does NOT match the legacy "`.trash_<ts>`" pattern.
     */
    fun isContainerDir(name: String): Boolean {
        return name == CONTAINER_NAME
    }

    /**
     * Parse the snapshot timestamp from a directory name placed directly inside `.trash/`.
     * Returns the parsed `Long` for numeric names; `null` otherwise.
     */
    fun parseSnapshotTimestamp(snapshotDirName: String): Long? {
        return snapshotDirName.toLongOrNull()
    }

    /**
     * Return true when [name] follows the legacy "`.trash_<ts>`" pattern.
     * Used by cleaner migration logic only — never write directories that match this.
     */
    fun isLegacyContainerDir(name: String): Boolean {
        return name.startsWith(LEGACY_PREFIX)
    }

    /**
     * Return true when [name] is any recognised trash-related path segment.
     * Matches the canonical container and the legacy migration directories only.
     */
    fun matchesTrashSegment(name: String): Boolean {
        return isContainerDir(name) || isLegacyContainerDir(name)
    }

    /**
     * Parse the timestamp suffix from a legacy "`.trash_<ts>`" directory name.
     * Returns the parsed `Long` for numeric suffixes; `null` otherwise.
     */
    fun parseLegacyTimestamp(legacyDirName: String): Long? {
        if (!legacyDirName.startsWith(LEGACY_PREFIX)) return null
        return legacyDirName.removePrefix(LEGACY_PREFIX).toLongOrNull()
    }

    /** Return true when [path] contains any recognised trash path segment. */
    fun containsTrashSegment(path: String): Boolean {
        return path.replace('\\', '/').split('/').any(::matchesTrashSegment)
    }
}
