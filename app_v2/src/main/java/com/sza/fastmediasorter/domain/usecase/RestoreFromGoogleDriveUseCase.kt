package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Restores settings and resources from Google Drive backup.
 * Settings are fully replaced. Resources are appended (duplicates skipped).
 */
class RestoreFromGoogleDriveUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val googleDriveClient: GoogleDriveRestClient,
    private val favoritesDao: FavoritesDao
) {
    companion object {
        private const val FOLDER_NAME = "FastMediaSorter"
        private const val FILE_NAME_PREFIX = "backup_"
    }

    /** Metadata about the backup file, shown in confirmation dialog. */
    data class BackupInfo(
        val createdAt: String,
        val resourceCount: Int,
        val appVersionName: String,
        val deviceModel: String,
        val favoritesCount: Int = 0
    )

    /** Result of a restore operation. */
    data class RestoreResult(
        val settingsRestored: Boolean,
        val resourcesAdded: Int,
        val resourcesSkipped: Int,
        val resourcesNeedingAuth: Int,
        val favoritesAdded: Int = 0,
        val favoritesSkipped: Int = 0
    )

    /**
     * Fetches backup metadata without downloading the full file.
     * Used to show info in the confirmation dialog.
     */
    suspend fun getBackupInfo(): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            if (!googleDriveClient.isAuthenticated()) {
                return@withContext Result.failure(Exception("Not authenticated with Google Drive"))
            }

            val payload = downloadAndParseBackup()
                ?: return@withContext Result.failure(Exception("No backup found"))

            Result.success(
                BackupInfo(
                    createdAt = payload.createdAt.orEmpty(),
                    resourceCount = payload.resources?.size ?: 0,
                    appVersionName = payload.appVersionName.orEmpty(),
                    deviceModel = payload.deviceModel.orEmpty(),
                    favoritesCount = payload.favorites?.size ?: 0
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get backup info")
            Result.failure(e)
        }
    }

    /**
     * Performs the full restore: settings replaced, resources appended with dedup.
     */
    suspend operator fun invoke(): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            if (!googleDriveClient.isAuthenticated()) {
                return@withContext Result.failure(Exception("Not authenticated with Google Drive"))
            }

            val payload = downloadAndParseBackup()
                ?: return@withContext Result.failure(Exception("No backup found"))

            // Check version compatibility
            if (payload.version > BackupPayload.CURRENT_VERSION) {
                return@withContext Result.failure(
                    Exception("Backup was created by a newer app version (${payload.appVersionName.orEmpty()}). Please update the app.")
                )
            }
            if (payload.version < BackupPayload.CURRENT_VERSION) {
                Timber.w("Restoring older backup format v${payload.version} (current v${BackupPayload.CURRENT_VERSION}) — unknown fields will use defaults")
            }

            // 1. Restore settings (full replace, keep credentials)
            val currentSettings = settingsRepository.getSettings().first()
            val restoredSettings = BackupMapper.toAppSettings(payload.settings ?: BackupSettings(), currentSettings)
            settingsRepository.updateSettings(restoredSettings)
            Timber.i("Settings restored from backup")

            // 2. Merge resources (append with dedup)
            val existingResources = resourceRepository.getAllResourcesSync()
            val resourcesToAdd = mutableListOf<MediaResource>()
            var skipped = 0
            var needsAuth = 0

            for (backupRes in payload.resources.orEmpty()) {
                try {
                    val candidate = BackupMapper.toMediaResource(backupRes)
                    val isDuplicate = existingResources.any { existing ->
                        isDuplicateResource(existing, candidate)
                    }
                    if (isDuplicate) {
                        skipped++
                    } else {
                        resourcesToAdd.add(candidate)
                        // Count resources that need re-authentication
                        if (candidate.type != ResourceType.LOCAL) {
                            needsAuth++
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Skipping resource '%s' — incompatible with current version", backupRes.name)
                    skipped++
                }
            }

            // Add new resources
            for (resource in resourcesToAdd) {
                resourceRepository.addResource(resource)
            }

            // 3. Merge favorites (append with dedup by URI)
            var favoritesAdded = 0
            var favoritesSkipped = 0
            val allResources = resourceRepository.getAllResourcesSync()
            val resourcesByPath = allResources.associateBy { it.path }

            for (backupFav in payload.favorites.orEmpty()) {
                val isDuplicate = favoritesDao.isFavoriteSync(backupFav.uri)
                if (isDuplicate) {
                    favoritesSkipped++
                    continue
                }
                val localResource = resourcesByPath[backupFav.resourcePath]
                if (localResource == null) {
                    favoritesSkipped++
                    continue
                }
                try {
                    val entity = BackupMapper.toFavoritesEntity(backupFav, localResource.id)
                    favoritesDao.insert(entity)
                    favoritesAdded++
                } catch (e: Exception) {
                    Timber.w(e, "Skipping favorite '%s'", backupFav.displayName)
                    favoritesSkipped++
                }
            }

            Timber.i("Restore complete: ${resourcesToAdd.size} added, $skipped skipped, $needsAuth need auth, $favoritesAdded favorites added, $favoritesSkipped favorites skipped")

            Result.success(
                RestoreResult(
                    settingsRestored = true,
                    resourcesAdded = resourcesToAdd.size,
                    resourcesSkipped = skipped,
                    resourcesNeedingAuth = needsAuth,
                    favoritesAdded = favoritesAdded,
                    favoritesSkipped = favoritesSkipped
                )
            )
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Backup file is corrupted")
            Result.failure(Exception("Backup file is damaged. Cannot restore."))
        } catch (e: Exception) {
            Timber.e(e, "Restore from Google Drive failed")
            Result.failure(e)
        }
    }

    private suspend fun downloadAndParseBackup(): BackupPayload? {
        // Find folder by exact name
        val folderResult = googleDriveClient.findFolderByName(FOLDER_NAME)
        val folderId = when (folderResult) {
            is CloudResult.Success -> folderResult.data?.id
            is CloudResult.Error -> null
        } ?: return null

        // List files in folder and find latest backup (backup_YYMMDD-HHmm.json pattern)
        val listResult = googleDriveClient.listFiles(folderId, null)
        val fileId = when (listResult) {
            is CloudResult.Success -> listResult.data.first
                .filter { it.name.startsWith(FILE_NAME_PREFIX) && it.name.endsWith(".json") }
                .maxByOrNull { it.modifiedDate }
                ?.id
            is CloudResult.Error -> null
        } ?: return null

        // Download
        val outputStream = ByteArrayOutputStream()
        val downloadResult = googleDriveClient.downloadFile(fileId, outputStream, null)
        return when (downloadResult) {
            is CloudResult.Success -> {
                val json = outputStream.toString(Charsets.UTF_8.name())
                GsonBuilder().setLenient().create().fromJson(json, BackupPayload::class.java)
            }
            is CloudResult.Error -> {
                Timber.e("Download failed: ${downloadResult.message}")
                null
            }
        }
    }

    /**
     * Dedup logic per spec:
     * - Regular resources: match by type + path
     * - Cloud resources: match by cloudProvider + cloudFolderId + accountId
     */
    private fun isDuplicateResource(existing: MediaResource, candidate: MediaResource): Boolean {
        if (existing.type == ResourceType.CLOUD && candidate.type == ResourceType.CLOUD) {
            return existing.cloudProvider == candidate.cloudProvider
                    && existing.cloudFolderId == candidate.cloudFolderId
                    && existing.accountId == candidate.accountId
        }
        return existing.type == candidate.type && existing.path == candidate.path
    }
}
