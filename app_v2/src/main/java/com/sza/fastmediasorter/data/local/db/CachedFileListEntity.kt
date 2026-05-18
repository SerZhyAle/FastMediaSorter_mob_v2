package com.sza.fastmediasorter.data.local.db

import androidx.room.*

/**
 * Stores a cached file list for a single resource as ONE row.
 *
 * [compressedData] - GZIP-compressed JSON array of all MediaFile objects.
 * Typical compression ratio for repetitive path/name text: 8–12×
 * (e.g. 10 000 files × ~400 B raw = 4 MB → ~350 KB compressed).
 *
 * Schema change (migration 8→9): replaced N-rows-per-resource design
 * (one row per MediaFile, plain TEXT) with a single BLOB row per resource.
 *
 * Schema change (migration 12→13, A5-T1): Added scan state fields:
 * - [lastScanTimestamp]: when the most recent scan completed (epoch ms).
 * - [lastModifiedFolder]: mtime of the source folder at scan time (epoch ms).
 *   Used for quick change detection - if folder mtime unchanged, skip full rescan.
 */
@Entity(
    tableName = "cached_file_lists",
    indices = [
        Index(value = ["resourceId"], unique = true, name = "idx_cached_files_resource_id")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CachedFileListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val resourceId: Long,

    /** GZIP-compressed JSON array of MediaFile objects. */
    @ColumnInfo(name = "compressed_data", typeAffinity = ColumnInfo.BLOB)
    val compressedData: ByteArray,

    /** Number of files in this snapshot (for quick stats without decompression). */
    @ColumnInfo(name = "file_count")
    val fileCount: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // ── A5 scan state fields (A5-T1) ─────────────────────────────────────────

    /**
     * Epoch ms timestamp when the last successful scan completed.
     * Null for entries created before A5 migration (treat as "never scanned incrementally").
     */
    @ColumnInfo(name = "last_scan_timestamp")
    val lastScanTimestamp: Long? = null,

    /**
     * Modification time (epoch ms) of the source folder/root at the time of the last scan.
     * For LOCAL: the folder's `lastModified()` value.
     * For NETWORK/CLOUD: may reflect content-change token or listing ETag.
     * Null = unknown / not yet populated.
     */
    @ColumnInfo(name = "last_modified_folder")
    val lastModifiedFolder: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedFileListEntity) return false
        return id == other.id && resourceId == other.resourceId && fileCount == other.fileCount
    }

    override fun hashCode(): Int = 31 * id.hashCode() + resourceId.hashCode()
}
