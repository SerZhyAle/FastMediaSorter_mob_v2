package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// ── JSON serialization data classes for favorites export/import ──────────────

// S1632: the export file is meant to travel between devices and app versions, so every key is pinned
// with @SerializedName. Without it R8 renames the fields and a release build writes {"a":..,"b":..},
// which no other build - and no human - can read back. The annotation also survives a property rename
// in code, which a keep rule would not.

data class FavoritesExportFile(
    @SerializedName("version") val version: String = "1.0",
    @SerializedName("exportDate") val exportDate: String,
    @SerializedName("appVersion") val appVersion: String,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("favorites") val favorites: List<ExportedFavorite>
)

data class ExportedFavorite(
    @SerializedName("uri") val uri: String,
    @SerializedName("resourceId") val resourceId: Long,
    @SerializedName("resourceName") val resourceName: String,
    @SerializedName("resourcePath") val resourcePath: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("mediaType") val mediaType: Int,
    @SerializedName("size") val size: Long,
    @SerializedName("dateModified") val dateModified: Long,
    @SerializedName("addedTimestamp") val addedTimestamp: Long
)

// ── Import result types ───────────────────────────────────────────────────────

data class FavoritesImportResult(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val unresolved: Int,
    val details: List<FavoritesImportDetail>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class FavoritesImportDetail(
    val status: FavoritesImportStatus,
    val displayName: String,
    val resourceName: String,
    val reason: String? = null
)

enum class FavoritesImportStatus { ADDED, SKIPPED, UNRESOLVED, FAILED }

enum class FavoritesConflictStrategy { SKIP, OVERWRITE }

// ── Export result ─────────────────────────────────────────────────────────────

data class FavoritesExportResult(
    val filePath: String,
    val totalExported: Int,
    val fileSizeBytes: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

// ── Import preview (shown before confirming import) ──────────────────────────

data class FavoritesImportPreview(
    val sourceDevice: String,
    val exportDate: String,
    val totalInFile: Int,
    val alreadyExisting: Int,
    val willBeAdded: Int
)
