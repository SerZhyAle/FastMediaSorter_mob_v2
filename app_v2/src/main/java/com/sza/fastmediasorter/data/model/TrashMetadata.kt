package com.sza.fastmediasorter.data.model

import com.google.gson.Gson

/**
 * Metadata for files stored in .trash folder.
 * Stores information needed to restore deleted files.
 *
 * Serialized/deserialized via Gson so it works in both production and JVM unit tests.
 * Field names map directly to JSON keys — do not rename without a migration path.
 */
data class TrashMetadata(
    val originalPath: String,           // Full path where file was located
    val resourceId: Long,                // ID of the resource
    val resourceType: String,            // "Local", "SMB", "SFTP", "FTP", "Cloud"
    val deletedFiles: List<String>,      // Names of files in .trash
    val deletionTimestamp: Long,         // When files were deleted (System.currentTimeMillis())
    val isDirectory: Boolean = false     // Whether this was a directory or single file(s)
) {
    /**
     * Serialize metadata to JSON string.
     */
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        /**
         * Deserialize metadata from JSON string.
         * Throws if the JSON is malformed or required fields are missing.
         */
        fun fromJson(json: String): TrashMetadata {
            val metadata = gson.fromJson(json, TrashMetadata::class.java)
                ?: throw IllegalArgumentException("JSON deserialized to null")
            // Validate required fields so callers get a clear error instead of silent nulls.
            requireNotNull(metadata.originalPath) { "originalPath missing" }
            requireNotNull(metadata.resourceType) { "resourceType missing" }
            requireNotNull(metadata.deletedFiles) { "deletedFiles missing" }
            return metadata
        }
    }
}
