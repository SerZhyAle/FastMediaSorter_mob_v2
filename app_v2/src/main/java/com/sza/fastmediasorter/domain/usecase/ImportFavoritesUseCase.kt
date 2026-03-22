package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.domain.model.ExportedFavorite
import com.sza.fastmediasorter.domain.model.FavoritesConflictStrategy
import com.sza.fastmediasorter.domain.model.FavoritesExportFile
import com.sza.fastmediasorter.domain.model.FavoritesImportDetail
import com.sza.fastmediasorter.domain.model.FavoritesImportPreview
import com.sza.fastmediasorter.domain.model.FavoritesImportResult
import com.sza.fastmediasorter.domain.model.FavoritesImportStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB
private const val SUPPORTED_MAJOR_VERSION = "1"

class ImportFavoritesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesDao: FavoritesDao,
    private val resourceDao: ResourceDao
) {

    private val gson = Gson()

    /** Parse and validate the file; return a preview without importing anything. */
    suspend fun preview(uri: Uri): Result<FavoritesImportPreview> {
        return try {
            val model = readAndParse(uri).getOrThrow()
            val existingUris = favoritesDao.getFavoriteUrisForPaths(model.favorites.map { it.uri })
            val alreadyExisting = existingUris.size
            val willBeAdded = model.favorites.size - alreadyExisting
            Result.success(
                FavoritesImportPreview(
                    sourceDevice = model.deviceName,
                    exportDate = model.exportDate,
                    totalInFile = model.totalCount,
                    alreadyExisting = alreadyExisting,
                    willBeAdded = willBeAdded.coerceAtLeast(0)
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Favorites import preview failed")
            Result.failure(e)
        }
    }

    /** Execute the import with the chosen conflict strategy. */
    suspend operator fun invoke(
        uri: Uri,
        strategy: FavoritesConflictStrategy
    ): FavoritesImportResult {
        return try {
            val model = readAndParse(uri).getOrThrow()

            // Build resource path → id lookup
            val allResources = resourceDao.getAllResourcesSync()
            val resourcesByPath = allResources.associateBy { it.path }

            val existingUris = favoritesDao.getFavoriteUrisForPaths(model.favorites.map { it.uri }).toSet()

            val details = mutableListOf<FavoritesImportDetail>()
            var imported = 0
            var skipped = 0
            var failed = 0
            var unresolved = 0

            for (exported in model.favorites) {
                val isDuplicate = exported.uri in existingUris

                // Resolve local resource id via path match
                val localResource = resourcesByPath[exported.resourcePath]

                when {
                    localResource == null -> {
                        unresolved++
                        details += FavoritesImportDetail(
                            status = FavoritesImportStatus.UNRESOLVED,
                            displayName = exported.displayName,
                            resourceName = exported.resourceName,
                            reason = "Resource not found on this device"
                        )
                    }

                    isDuplicate && strategy == FavoritesConflictStrategy.SKIP -> {
                        skipped++
                        details += FavoritesImportDetail(
                            status = FavoritesImportStatus.SKIPPED,
                            displayName = exported.displayName,
                            resourceName = exported.resourceName,
                            reason = "Already in favorites"
                        )
                    }

                    else -> {
                        try {
                            val entity = exported.toEntity(localResource.id)
                            favoritesDao.insert(entity)
                            imported++
                            details += FavoritesImportDetail(
                                status = FavoritesImportStatus.ADDED,
                                displayName = exported.displayName,
                                resourceName = exported.resourceName
                            )
                        } catch (e: Exception) {
                            failed++
                            Timber.e(e, "Failed to insert favorite: ${exported.displayName}")
                            details += FavoritesImportDetail(
                                status = FavoritesImportStatus.FAILED,
                                displayName = exported.displayName,
                                resourceName = exported.resourceName,
                                reason = e.message
                            )
                        }
                    }
                }
            }

            Timber.d("Favorites import complete: imported=$imported skipped=$skipped failed=$failed unresolved=$unresolved")
            FavoritesImportResult(
                imported = imported,
                skipped = skipped,
                failed = failed,
                unresolved = unresolved,
                details = details,
                isSuccess = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Favorites import failed")
            FavoritesImportResult(
                imported = 0,
                skipped = 0,
                failed = 0,
                unresolved = 0,
                details = emptyList(),
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    private fun readAndParse(uri: Uri): Result<FavoritesExportFile> {
        return try {
            val contentResolver = context.contentResolver

            // Check file size
            val size = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            if (size > MAX_FILE_SIZE_BYTES) {
                return Result.failure(IllegalArgumentException("File too large (max 10 MB)"))
            }

            val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return Result.failure(IllegalStateException("Cannot read file"))

            val model = gson.fromJson(json, FavoritesExportFile::class.java)
                ?: return Result.failure(JsonParseException("Invalid JSON structure"))

            // Version check (major version must match)
            val majorVersion = model.version.substringBefore(".")
            if (majorVersion != SUPPORTED_MAJOR_VERSION) {
                return Result.failure(IllegalArgumentException("Unsupported file version: ${model.version}"))
            }

            Result.success(model)
        } catch (e: JsonParseException) {
            Timber.e(e, "Invalid favorites JSON")
            Result.failure(IllegalArgumentException("File is corrupted or invalid"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to read favorites file")
            Result.failure(e)
        }
    }

    private fun ExportedFavorite.toEntity(localResourceId: Long) = FavoritesEntity(
        uri = uri,
        resourceId = localResourceId,
        displayName = displayName,
        mediaType = mediaType,
        size = size,
        lastKnownPath = uri,
        dateModified = dateModified,
        addedTimestamp = addedTimestamp
    )
}
