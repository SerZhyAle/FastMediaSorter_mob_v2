package com.sza.fastmediasorter.data.local.db

import androidx.room.*

/**
 * Persists file-level metadata for fast incremental scan and thumbnail/EXIF caching (A5).
 *
 * Cache validity is determined by comparing [lastModified] + [fileSize] with
 * the current filesystem values. If either changes, the entry must be refreshed.
 *
 * [provider] mirrors [ResourceType] name so entries from different source types
 * for the same resourceId can coexist (edge case: cloned resources).
 *
 * [credentialsId] allows targeted cache invalidation when network credentials change.
 */
@Entity(
    tableName = "file_metadata_cache",
    indices = [
        Index(value = ["resourceId", "filePath"], unique = true, name = "idx_fmc_resource_path"),
        Index(value = ["credentialsId"], name = "idx_fmc_credentials_id"),
        Index(value = ["cachedAt"], name = "idx_fmc_cached_at")
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
data class FileMetadataCacheEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Parent resource ID. CASCADE delete keeps cache consistent with resources. */
    val resourceId: Long,

    /** Relative path of the file within the resource root. */
    val filePath: String,

    /** ResourceType name (LOCAL, SMB, SFTP, FTP, CLOUD_GOOGLE, etc.). */
    val provider: String,

    /** Credential ID used to access the resource (null for LOCAL). */
    val credentialsId: String?,

    // ── Invalidation fields ───────────────────────────────────────────────────

    /** File modification time (epoch ms) at the time of caching. */
    val lastModified: Long,

    /** File size in bytes at the time of caching. */
    val fileSize: Long,

    /** Epoch ms when this cache entry was written. Used for TTL expiry. */
    val cachedAt: Long,

    // ── Cached metadata (all nullable — may not be available for all file types) ──

    /** Absolute path to locally cached thumbnail image. */
    val thumbnailPath: String?,

    /** Media duration in milliseconds (video/audio only). */
    val durationMs: Long?,

    /** Video/image width in pixels. */
    val width: Int?,

    /** Video/image height in pixels. */
    val height: Int?,

    /** EXIF metadata serialised as a JSON object string. */
    val exifJson: String?
)
