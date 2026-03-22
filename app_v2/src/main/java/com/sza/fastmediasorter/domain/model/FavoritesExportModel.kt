package com.sza.fastmediasorter.domain.model

// ── JSON serialization data classes for favorites export/import ──────────────

data class FavoritesExportFile(
    val version: String = "1.0",
    val exportDate: String,
    val appVersion: String,
    val deviceName: String,
    val totalCount: Int,
    val favorites: List<ExportedFavorite>
)

data class ExportedFavorite(
    val uri: String,
    val resourceId: Long,
    val resourceName: String,
    val resourcePath: String,
    val displayName: String,
    val mediaType: Int,
    val size: Long,
    val dateModified: Long,
    val addedTimestamp: Long
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
