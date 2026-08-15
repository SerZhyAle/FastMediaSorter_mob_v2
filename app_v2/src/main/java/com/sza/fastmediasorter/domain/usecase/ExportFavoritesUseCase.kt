package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Build
import android.os.Environment
import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.domain.model.ExportedFavorite
import com.sza.fastmediasorter.domain.model.FavoritesExportFile
import com.sza.fastmediasorter.domain.model.FavoritesExportResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ExportFavoritesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesDao: FavoritesDao,
    private val resourceDao: ResourceDao
) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val fileDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneOffset.UTC)
    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    suspend operator fun invoke(): FavoritesExportResult {
        return try {
            // S0783: export file favorites only; live-channel favorites are not part of the favorites
            // export/import contract (they live in the streams catalog).
            val favorites = favoritesDao.getFileFavoritesSync()
            if (favorites.isEmpty()) {
                return writeToFile(
                    FavoritesExportFile(
                        exportDate = isoFormatter.format(Instant.now()),
                        appVersion = BuildConfig.VERSION_NAME,
                        deviceName = Build.MODEL,
                        totalCount = 0,
                        favorites = emptyList()
                    )
                )
            }

            // Build resource lookup map (id → entity)
            val resourceIds = favorites.map { it.resourceId }.toSet()
            val resourceMap = resourceIds.mapNotNull { id ->
                resourceDao.getResourceByIdSync(id)?.let { id to it }
            }.toMap()

            val exportedFavorites = favorites.map { entity ->
                val resource = resourceMap[entity.resourceId]
                ExportedFavorite(
                    uri = entity.uri,
                    resourceId = entity.resourceId,
                    resourceName = resource?.name ?: "",
                    resourcePath = resource?.path ?: "",
                    displayName = entity.displayName,
                    mediaType = entity.mediaType,
                    size = entity.size,
                    dateModified = entity.dateModified,
                    addedTimestamp = entity.addedTimestamp
                )
            }

            writeToFile(
                FavoritesExportFile(
                    exportDate = isoFormatter.format(Instant.now()),
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceName = Build.MODEL,
                    totalCount = exportedFavorites.size,
                    favorites = exportedFavorites
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Export favorites failed")
            FavoritesExportResult(
                filePath = "",
                totalExported = 0,
                fileSizeBytes = 0,
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    private fun writeToFile(exportFile: FavoritesExportFile): FavoritesExportResult {
        val timestamp = fileDateFormatter.format(Instant.now())
        val fileName = "favorites_export_$timestamp.json"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outputFile = File(downloadsDir, fileName)

        val json = gson.toJson(exportFile)
        Timber.d("S1632: export written, real keys=${json.startsWith("{\"version\"")}")
        outputFile.writeText(json, Charsets.UTF_8)

        Timber.d("Favorites exported to ${outputFile.absolutePath}, count=${exportFile.totalCount}")
        return FavoritesExportResult(
            filePath = outputFile.absolutePath,
            totalExported = exportFile.totalCount,
            fileSizeBytes = outputFile.length(),
            isSuccess = true
        )
    }
}
